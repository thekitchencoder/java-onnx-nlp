#!/usr/bin/env python3
"""
Standalone ONNX training script (no imports from this repository).

It trains simple text classifiers (one per label head) using
TF-IDF(word) + LogisticRegression, evaluates them, exports each to ONNX,
and writes per-head ModelBundle directories compatible with the Java
FileSystemModelLoader:
  <target>/<head>/
    - model.onnx
    - config.json
    - calibration.json (optional)

Requirements (install with "pip install -r requirements.txt"):
  - pandas
  - numpy
  - scikit-learn
  - scipy
  - skl2onnx

Usage examples:
  - Basic (infers label columns starting with `label_`):
      python onnx_train.py training-data.csv

  - Explicit label columns and custom name:
      python onnx_train.py training-data.csv \
        --label-col address --label-col risk --name classifier

  - With config overrides, set opset and enable zipmap:
      python onnx_train.py training-data.csv \
        --config overrides.json --opset 13 --zipmap

Notes:
  - Label columns are expected to be either provided with `--label-col <name>`
    (repeatable), or inferred as all CSV columns beginning with `label_`.
  - For ONNX export compatibility, only word-level TF-IDF features are used.
    Any character-level feature overrides are ignored with a warning.
  - Outputs per head go into ModelBundle directories under the target folder (default: "models").
    For example, with heads `address` and `risk` and `--name models`, the script creates:
      - models/address/{model.onnx,config.json,calibration.json}
      - models/risk/{model.onnx,config.json,calibration.json}
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, List, Tuple

import numpy as np
import pandas as pd
import time

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.calibration import calibration_curve

from scipy.optimize import curve_fit

from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import StringTensorType


# ----------------------------- Utility Bits ----------------------------------

def name_of(dimension: str) -> str:
    return dimension.replace("label_", "").lower()


def parse_config_overrides(config: Path | None) -> Dict[str, Dict[str, Any]]:
    overrides: Dict[str, Dict[str, Any]] = {}
    if config is None:
        return overrides
    try:
        raw = json.loads(Path(config).read_text())
        if not isinstance(raw, dict):
            raise ValueError("Overrides JSON must be an object mapping head->params")
        # Ensure all values are dicts
        for k, v in raw.items():
            if not isinstance(v, dict):
                raise ValueError(f"Overrides for head '{k}' must be an object of params")
        overrides = raw  # type: ignore[assignment]
        return overrides
    except Exception as e:
        raise SystemExit(f"Failed to read overrides JSON: {e}")


def parse_training_data(train_csv: Path, label_cols: Tuple[str, ...]) -> tuple[pd.DataFrame, List[str]]:
    try:
        df = pd.read_csv(train_csv)
    except Exception as e:
        raise SystemExit(f"Failed to read CSV: {e}")

    if "text" not in df.columns:
        raise SystemExit("Training CSV must contain a 'text' column.")

    # Infer label columns if none provided
    if label_cols:
        dimensions = list(label_cols)
    else:
        dimensions = [c for c in df.columns if c.startswith("label_")]

    if not dimensions:
        raise SystemExit("No label columns provided or inferred (need columns starting with 'label_').")

    df["text"] = df["text"].astype(str).str.strip()

    # Remove empty or very short texts
    before = len(df)
    df = df[df["text"].str.len() > 2].reset_index(drop=True)
    removed = before - len(df)
    if removed > 0:
        print(f"Filtered out {removed} very short/empty text rows.")

    # Ensure label columns exist and cast to int
    missing = [d for d in dimensions if d not in df.columns]
    if missing:
        raise SystemExit(f"Missing columns for dimensions: {missing}")
    for d in dimensions:
        try:
            df[d] = df[d].astype(int)
        except Exception:
            raise SystemExit(f"Label column '{d}' must be castable to int (0/1)")

    # Print dataset statistics
    print("\n" + "=" * 60)
    print("DATASET STATISTICS")
    print("=" * 60)
    print(f"Total samples: {len(df)}")
    print("\nClass distribution:")
    for d in dimensions:
        pos_count = int(df[d].sum())
        neg_count = len(df) - pos_count
        pct_pos = (pos_count / len(df) * 100.0) if len(df) else 0.0
        pct_neg = (neg_count / len(df) * 100.0) if len(df) else 0.0
        print(f"  {name_of(d)}: {pos_count} positive ({pct_pos:.1f}%), {neg_count} negative ({pct_neg:.1f}%)")
        if pos_count < 50:
            print(f"    ⚠️  WARNING: Only {pos_count} positive examples for {name_of(d)}. Recommend at least 100+")

    return df, dimensions


def split_training_data(dimension: str, df: pd.DataFrame, test_size: float, random_state: int = 42):
    print(f"\nTraining {name_of(dimension)} classifier...")

    y_all = df[dimension].values

    # Check if we have both classes
    if len(set(int(v) for v in set(y_all))) < 2:
        print(f"WARNING: Only one class present for {name_of(dimension)}. Skipping.")
        return None

    # Stratified split ensures both train and test have similar class distributions
    try:
        train_idx, test_idx = train_test_split(
            range(len(df)),
            test_size=test_size,
            random_state=random_state,
            stratify=y_all,
        )
    except ValueError as e:
        print(
            f"WARNING: Could not stratify split for {name_of(dimension)} (too few examples of one class): {e}"
        )
        print("Falling back to random split...")
        train_idx, test_idx = train_test_split(
            range(len(df)), test_size=test_size, random_state=random_state
        )

    x_train = df.iloc[train_idx]["text"].values
    x_test = df.iloc[test_idx]["text"].values
    y_train = df.iloc[train_idx][dimension].values
    y_test = df.iloc[test_idx][dimension].values

    print(
        f"  Train: {len(y_train)} samples ({int(sum(y_train))} positive, {len(y_train) - int(sum(y_train))} negative)"
    )
    print(
        f"  Test:  {len(y_test)} samples ({int(sum(y_test))} positive, {len(y_test) - int(sum(y_test))} negative)"
    )

    return (x_train, y_train), (x_test, y_test)


# -------------------------- Modeling Bobs ---------------------------------

def make_text_classifier(
        strip_accents: str | None = None,
        use_word_features: bool = True,
        word_ngram_range: tuple = (1, 2),
        word_min_df: int = 1,
        C: float = 0.5,
):
    if not use_word_features:
        raise SystemExit("For ONNX export, word features must be enabled.")

    feats = TfidfVectorizer(
        analyzer="word",
        ngram_range=word_ngram_range,
        min_df=word_min_df,
        lowercase=True,
        strip_accents=strip_accents,
        norm="l2",
        sublinear_tf=True,
    )

    lr = LogisticRegression(C=C, max_iter=1000, random_state=42)

    return Pipeline([
        ("feats", feats),
        ("lr", lr),
    ])


def evaluate_head(name: str, model: Pipeline, X_test, y_test) -> Dict[str, float]:
    proba = model.predict_proba(X_test)[:, 1]
    y_pred = (proba >= 0.5).astype(int)

    print(f"\n=== {name} head ===")
    try:
        auc = roc_auc_score(y_test, proba)
    except Exception:
        auc = float("nan")

    metrics = {
        "accuracy": float(accuracy_score(y_test, y_pred)),
        "precision": float(precision_score(y_test, y_pred, zero_division=0)),
        "recall": float(recall_score(y_test, y_pred, zero_division=0)),
        "f1": float(f1_score(y_test, y_pred, zero_division=0)),
        "roc_auc": float(auc) if not np.isnan(auc) else 0.0,
    }

    print(
        f"  accuracy={metrics['accuracy']:.4f}  precision={metrics['precision']:.4f}  "
        f"recall={metrics['recall']:.4f}  f1={metrics['f1']:.4f}  roc_auc={metrics['roc_auc']:.4f}"
    )
    return metrics


def training_summary(all_metrics: Dict[str, Dict[str, float]]):
    print("\n" + "=" * 70)
    print("TRAINING SUMMARY")
    print("=" * 67)
    print("\n⚠️  PERFORMANCE WARNINGS:")
    for d, metrics in all_metrics.items():
        if metrics:
            roc_auc = metrics.get("roc_auc", 0.0)
            head = name_of(d)
            if roc_auc < 0.7:
                print(f"  • {head}: ROC-AUC={roc_auc:.4f} - POOR (need more training data)")
            elif roc_auc < 0.85:
                print(f"  • {head}: ROC-AUC={roc_auc:.4f} - FAIR (could use more data)")
            elif roc_auc > 0.95:
                print(f"  ! {head}: ROC-AUC={roc_auc:.4f} - OVERFITTING (more data / less noise)")
            else:
                print(f"  ✓ {head}: ROC-AUC={roc_auc:.4f} - GOOD")

    print("\n" + "=" * 70)
    print("RECOMMENDATIONS:")
    print("=" * 70)
    print("1. Add more training data (aim for 500-1000+ rows)")
    print("2. Ensure each category has 100+ positive examples")
    print("3. Include more examples with single clear labels")
    print("4. Add domain-specific keywords to lexicons if needed")
    print("=" * 70 + "\n")


# ------------------------------ CLI Widgets ---------------------------------------

def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        prog="onnx_train.py",
        description="Train ONNX-exportable multi-head text classifiers (standalone).",
    )

    parser.add_argument(
        "train_csv",
        type=Path,
        help="Path to the training CSV file",
    )

    parser.add_argument(
        "--label-col",
        dest="label_cols",
        action="append",
        default=None,
        help=(
            "Label column (e.g., address). Repeat for multiple heads. "
            "If omitted, columns starting with 'label_' are inferred."
        ),
    )

    parser.add_argument(
        "--name",
        default="models",
        help=(
            "Target output directory for ModelBundles. Each head will be saved under "
            "<name>/<head> (default: %(default)s)"
        ),
    )

    parser.add_argument(
        "--config",
        type=Path,
        default=None,
        help="Path to JSON with sparse overrides per head",
    )

    parser.add_argument(
        "--opset",
        type=int,
        default=13,
        help="Target ONNX opset version (default: %(default)s)",
    )

    zipmap_group = parser.add_mutually_exclusive_group()
    zipmap_group.add_argument(
        "--zipmap",
        dest="zipmap",
        action="store_true",
        help="Use ZipMap for probabilities",
    )
    zipmap_group.add_argument(
        "--no-zipmap",
        dest="zipmap",
        action="store_false",
        help="Do not use ZipMap for probabilities (recommended)",
    )
    parser.set_defaults(zipmap=False)

    return parser.parse_args(argv)


# --------------------------- Training Stuff --------------------------------

def main(argv: List[str]) -> int:
    args = parse_args(argv)

    print("\n=== ONNX TRAINER ===")
    print(f"Input CSV: {args.train_csv}")
    print(f"Output dir (--name): {args.name}")
    if args.config:
        print(f"Overrides JSON: {args.config}")
    print("")

    if not args.train_csv.exists() or not args.train_csv.is_file():
        print(f"error: training CSV not found or not a file: {args.train_csv}", file=sys.stderr)
        return 2
    if args.config is not None and (not args.config.exists() or not args.config.is_file()):
        print(f"error: config path not found or not a file: {args.config}", file=sys.stderr)
        return 2

    label_cols: Tuple[str, ...] = tuple(args.label_cols) if args.label_cols else tuple()

    # Load training data and inferred dimensions
    t0 = time.perf_counter()
    df, dimensions = parse_training_data(args.train_csv, label_cols)
    t1 = time.perf_counter()
    heads = [name_of(d) for d in dimensions]
    print(f"Loaded dataset and prepared labels in {t1 - t0:.2f}s; heads: {', '.join(heads)}")

    # Load overrides JSON if provided (sparse per-head overrides)
    overrides = parse_config_overrides(args.config)

    base_out_dir = Path(args.name)
    base_out_dir.mkdir(parents=True, exist_ok=True)

    saved_paths: Dict[str, str] = {}
    all_metrics: Dict[str, Dict[str, float]] = {}

    print("\n" + "=" * 60)
    print("TRAINING CLASSIFIERS")
    print("=" * 60)

    for idx, d in enumerate(dimensions, start=1):
        params = overrides.get(d, {})
        default_params = {
            "strip_accents": None,
            "use_word_features": True,
            "use_char_features": False,  # ignored for ONNX
            "word_ngram_range": (1, 2),
            "word_min_df": 1,
            "calibrate": False,  # not used here; kept for parity
            "C": 0.5,
        }
        # Apply overrides with defensive copy
        model_params = dict(default_params)
        model_params.update({k: v for k, v in params.items() if k in default_params})

        # ONNX export limitation: no char-level features in skl2onnx conversion
        if params.get("char_ngram_range") or params.get("char_min_df"):
            print(f"[onnx train] Warning: char-level features requested for '{d}' are ignored for ONNX export.")

        print(f"\n[{idx}/{len(dimensions)}] Head: {name_of(d)} — preparing train/test split…")
        split = split_training_data(dimension=d, df=df, test_size=0.2, random_state=42)
        if split is None:
            all_metrics[d] = {}
            continue

        (x_train, y_train), (x_test, y_test) = split

        # No char features for ONNX :-(
        pipeline = make_text_classifier(
            strip_accents=model_params["strip_accents"],
            use_word_features=model_params["use_word_features"],
            word_ngram_range=tuple(model_params["word_ngram_range"]),
            word_min_df=int(model_params["word_min_df"]),
            C=float(model_params["C"]),
        )

        print(f"[{idx}/{len(dimensions)}] {name_of(d)} — fitting model…", end="", flush=True)
        t_fit0 = time.perf_counter()
        pipeline.fit(x_train, y_train)
        t_fit1 = time.perf_counter()
        print(f" done in {t_fit1 - t_fit0:.2f}s")
        train_probs = pipeline.predict_proba(x_train)[:, 1]

        # Fit sigmoid: 1 / (1 + exp(-(a * x + b)))
        def sigmoid(x, a, b):
            return 1 / (1 + np.exp(-(a * x + b)))

        print(f"[{idx}/{len(dimensions)}] {name_of(d)} — calibrating…", end="", flush=True)
        prob_true, prob_pred = calibration_curve(y_train, train_probs, n_bins=10)
        if len(prob_pred) < 3 or np.std(prob_pred) < 0.01:
            print(" identity (insufficient variation)")
            calibration_params = {
                "a": 1.0,
                "b": 0.0,
                "type": "identity",
            }
        else:
            try:
                t_cal0 = time.perf_counter()
                # Convert predicted probabilities to log-odds (logit) space for standard Platt scaling
                logits = np.log(prob_pred / (1 - prob_pred + 1e-10))
                popt = curve_fit(
                    sigmoid,
                    logits,  # fit in logit space, not raw probability space
                    prob_true,
                    p0=[1, 0],
                    maxfev=5000,
                    bounds=([-10, -10], [10, 10]),
                )[0]
                t_cal1 = time.perf_counter()
                calibration_params = {
                    "a": float(popt[0]),
                    "b": float(popt[1]),
                    "type": "platt_scaling",
                }
                print(f" platt (a={calibration_params['a']:.3f}, b={calibration_params['b']:.3f}) in {t_cal1 - t_cal0:.2f}s")
            except Exception:
                print(" identity (calibration fit failed)")
                calibration_params = {
                    "a": 1.0,
                    "b": 0.0,
                    "type": "identity",
                }

        # Evaluate
        metrics = evaluate_head(name_of(d), pipeline, x_test, y_test)
        all_metrics[d] = metrics

        # Convert to ONNX
        options = {id(pipeline): {"zipmap": args.zipmap}}
        try:
            print(f"[{idx}/{len(dimensions)}] {name_of(d)} — exporting ONNX…", end="", flush=True)
            t_onx0 = time.perf_counter()
            onx = convert_sklearn(
                pipeline,
                initial_types=[("text", StringTensorType([None, 1]))],
                options=options,
                target_opset=args.opset,
            )
            t_onx1 = time.perf_counter()
            print(f" done in {t_onx1 - t_onx0:.2f}s")
        except Exception as e:
            print(f"Failed to convert head '{name_of(d)}' to ONNX: {e}", file=sys.stderr)
            return 1

        # Prepare ModelBundle directory for this head
        head = name_of(d)
        head_dir = base_out_dir / head
        head_dir.mkdir(parents=True, exist_ok=True)

        # Write model.onnx
        model_path = head_dir / "model.onnx"
        with open(model_path, "wb") as f:
            f.write(onx.SerializeToString())
        print(f"Saved ONNX model: {model_path}")

        # Build config.json compatible with ModelConfig
        # Determine class labels in the order [negative, positive]
        class_labels = [f"no_{head}", f"has_{head}"]

        # Determine tensor names used by exported model
        input_tensor_name = "text"  # set by initial_types above
        output_tensor_name = "probabilities"  # same for both zipmap modes

        config = {
            "modelName": head,
            "version": "1.0.0",
            "classLabels": class_labels,
            "inputTensorName": input_tensor_name,
            "outputTensorName": output_tensor_name,
            "maxSequenceLength": 512,
            "vocabulary": None,
            "metadata": {
                "ngram_range": list(model_params["word_ngram_range"]),
                "min_df": int(model_params["word_min_df"]),
                "zipmap": bool(args.zipmap),
                "feature": "tfidf_word",
            },
        }

        config_path = head_dir / "config.json"
        with open(config_path, "w") as f:
            json.dump(config, f, indent=2)
        print(f"Saved config: {config_path}")

        # Save calibration.json compatible with CalibrationData
        # Only write if calibration is not identity (a,b)=(1,0)
        if not (abs(calibration_params.get("a", 1.0) - 1.0) < 1e-9 and abs(calibration_params.get("b", 0.0)) < 1e-9):
            calib = {
                "calibrationType": "platt" if calibration_params.get("type") == "platt_scaling" else "identity",
                "parameters": {"a": float(calibration_params.get("a", 1.0)), "b": float(calibration_params.get("b", 0.0))},
            }
            calib_path = head_dir / "calibration.json"
            with open(calib_path, "w") as f:
                json.dump(calib, f, indent=2)
            print(f"Saved calibration: {calib_path}")

        saved_paths[head] = str(head_dir)

    # Print training summary
    training_summary(all_metrics)

    # Report saved bundle directories (no summary.json is written anymore)
    print(f"Saved ModelBundle directories: {saved_paths}")

    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
