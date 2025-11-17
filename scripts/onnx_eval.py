#!/usr/bin/env python3
"""
Standalone ONNX evaluation script (no imports from this repository).

It loads one or more ONNX models (multi-head text classifiers) by scanning a
base directory for per-head subdirectories and evaluates free-form text inputs,
producing binary predictions and
optional calibrated probabilities per head.

Discovery mode (only):
  - --name <dir>: Base directory produced by onnx_train.py (default: "models").
    This directory must contain one subdirectory per head, each a ModelBundle
    directory named after the head, e.g.:
      scripts/models/
        ├── address/
        ├── risk/
        └── voda/

Each ModelBundle directory must contain:
  - model.onnx
  - config.json (ModelConfig JSON)
  - calibration.json (optional; CalibrationData JSON)

Inputs can be provided via a positional argument or via stdin (one text per
line). If neither stdin nor positional arg is provided, the script exits.

Outputs by default are JSON Lines per input. CSV output is available with
--csv. Use --no-prob to omit probabilities and only print binary labels.

Calibration: if a calibration JSON is available and has
  { "calibrationType": "platt", "parameters": {"a": <double>, "b": <double>} }
then calibrated probability is p = 1 / (1 + exp(-(a * p + b))).

Requirements (install with "pip install -r requirements.txt"):
  - numpy
  - onnxruntime

Examples:
  # JSONL with probabilities (default)
  echo "hello world" | python scripts/onnx_eval.py --name scripts/models

  # CSV without header, thresholds per head
  python scripts/onnx_eval.py --name scripts/models \
    --csv --no-header --threshold address=0.6 --threshold risk=0.4 "some text"
"""
from __future__ import annotations

import argparse
import csv
import json
import os
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, List, Mapping, Optional, Tuple, Any

import numpy as np
import onnxruntime as ort


# ----------------------------- Helpers ----------------------------------------


def read_inputs_from_stdin_or_arg(arg_text: Optional[str]) -> List[str]:
    """Reads inputs either from the provided positional argument or from stdin.

    If stdin is a TTY and no arg provided, returns an empty list.
    """
    inputs: List[str] = []
    if arg_text is not None:
        s = str(arg_text).strip()
        if s:
            inputs.append(s)
    else:
        if not sys.stdin.isatty():
            for line in sys.stdin:
                s = line.strip()
                if s:
                    inputs.append(s)
    if not inputs:
        sys.exit("No input provided. Pass TEXT arg or pipe lines via stdin.")
    return inputs


@dataclass
class SessionBundle:
    head: str
    session: ort.InferenceSession
    input_name: str
    output_prob: str
    calibration: Optional[Mapping[str, float]]  # expects keys a, b (new schema under parameters)
    class_labels: List[str]


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text())


def _build_session_from_bundle(bundle_dir: Path) -> SessionBundle:
    """Builds a session bundle from a ModelBundle directory.

    Expects files: model.onnx, config.json, optional calibration.json.
    Uses config.inputTensorName and config.outputTensorName.
    """
    model_path = bundle_dir / "model.onnx"
    config_path = bundle_dir / "config.json"
    calib_path = bundle_dir / "calibration.json"

    if not model_path.exists():
        sys.exit(f"Model file not found: {model_path}")
    if not config_path.exists():
        sys.exit(f"Config file not found: {config_path}")

    config = _read_json(config_path)
    # Validate minimal fields
    try:
        input_name_cfg = config["inputTensorName"]
        output_name_cfg = config["outputTensorName"]
        class_labels = list(config.get("classLabels", []))
    except Exception as e:
        sys.exit(f"Invalid config.json at {config_path}: {e}")

    # Build session
    sess = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])  # eager load

    # Resolve input name: prefer config, otherwise pick the first available
    available_inputs = {i.name: i for i in sess.get_inputs()}
    if input_name_cfg in available_inputs:
        input_name = input_name_cfg
    else:
        # Fallback to a STRING input if present, else the first input
        string_inputs = [i.name for i in sess.get_inputs() if getattr(i, "type", "").endswith("string") or str(getattr(i, "type", "")).lower().find("string") >= 0]
        input_name = string_inputs[0] if string_inputs else sess.get_inputs()[0].name

    # Resolve output name: prefer config, otherwise infer a sensible default
    available_outputs = {o.name: o for o in sess.get_outputs()}
    if output_name_cfg in available_outputs:
        output_name = output_name_cfg
    else:
        # Heuristics:
        # 1) If only one output, use it
        # 2) Prefer an output with "prob" in the name
        # 3) Otherwise, prefer a FLOAT tensor with shape second dim == 2
        outs = sess.get_outputs()
        if len(outs) == 1:
            output_name = outs[0].name
        else:
            # 2) name contains prob
            prob_named = [o for o in outs if "prob" in o.name.lower()]
            if prob_named:
                output_name = prob_named[0].name
            else:
                # 3) try to find 2-class style tensor
                two_class = []
                for o in outs:
                    try:
                        shape = list(getattr(o, "shape", []) or [])
                        otype = str(getattr(o, "type", "")).lower()
                        if "float" in otype and len(shape) >= 2 and (shape[-1] == 2 or shape[1] == 2):
                            two_class.append(o)
                    except Exception:
                        pass
                if two_class:
                    output_name = two_class[0].name
                else:
                    # last resort: first output
                    output_name = outs[0].name

    # Load calibration if present (new schema)
    calibration: Optional[Mapping[str, float]] = None
    if calib_path.exists():
        try:
            calib_obj = _read_json(calib_path)
            if isinstance(calib_obj, dict) and calib_obj.get("calibrationType") == "platt":
                params = calib_obj.get("parameters", {}) or {}
                a = float(params.get("a", 1.0))
                b = float(params.get("b", 0.0))
                calibration = {"a": a, "b": b}
        except Exception:
            calibration = None

    head = bundle_dir.name
    return SessionBundle(
        head=head,
        session=sess,
        input_name=input_name,
        output_prob=output_name,
        calibration=calibration,
        class_labels=class_labels,
    )


def discover_onnx_models_dir(base_dir: Path) -> Dict[str, SessionBundle]:
    """Discover ModelBundle subdirectories under base_dir and build sessions.

    A valid head directory contains at least model.onnx and config.json.
    The head name is the directory name.
    """
    if not base_dir.exists() or not base_dir.is_dir():
        sys.exit(f"Base directory not found or not a directory: {base_dir}")

    # Warn if legacy summary.json exists (ignored)
    legacy_summary = base_dir / "summary.json"
    if legacy_summary.exists():
        print(f"Warning: Ignoring legacy summary.json at {legacy_summary}; using directory scanning instead.", file=sys.stderr)

    bundles: Dict[str, SessionBundle] = {}
    # Sort for deterministic order
    for child in sorted(base_dir.iterdir(), key=lambda p: p.name.lower()):
        if not child.is_dir():
            continue
        model_path = child / "model.onnx"
        config_path = child / "config.json"
        if model_path.exists() and config_path.exists():
            sb = _build_session_from_bundle(child)
            bundles[sb.head] = sb

    if not bundles:
        sys.exit(f"No models discovered under {base_dir}. Ensure it contains per-head subdirectories with model.onnx and config.json")
    return bundles


def parse_thresholds(heads: Iterable[str], overrides: Optional[Tuple[str, ...]]) -> Dict[str, float]:
    thr: Dict[str, float] = {h: 0.5 for h in heads}
    if overrides:
        for spec in overrides:
            if "=" not in spec:
                sys.exit(f"Invalid --threshold '{spec}' (expected head=VALUE)")
            k, v = spec.split("=", 1)
            try:
                thr[k] = float(v)
            except ValueError:
                sys.exit(f"Threshold for '{k}' must be a number, got '{v}'")
    return thr


# ------------------------------- CLI ------------------------------------------


def main(argv: Optional[List[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Evaluate ONNX models on input texts (JSONL or CSV output)")
    g = parser.add_argument_group("Model discovery")
    g.add_argument("text", nargs="?", help="Single input text. If omitted, reads lines from stdin.")
    g.add_argument(
        "--name",
        default="models",
        help="Base directory produced by onnx_train.py containing per-head subdirectories (default: %(default)s)",
    )

    o = parser.add_argument_group("Output")
    o.add_argument("--no-prob", dest="prob", action="store_false", help="Do not include probabilities in output")
    o.add_argument("--csv", dest="as_jsonl", action="store_false", help="Output CSV instead of JSONL")
    o.add_argument("--no-header", action="store_true", help="Do not print CSV header row")

    t = parser.add_argument_group("Thresholds")
    t.add_argument("--threshold", action="append", default=None, help="Per-head threshold as head=0.5 (repeatable)")

    args = parser.parse_args(argv)

    inputs = read_inputs_from_stdin_or_arg(args.text)
    base_dir = Path(args.name)
    sessions = discover_onnx_models_dir(base_dir)
    thr_by_head = parse_thresholds(sessions.keys(), tuple(args.threshold) if args.threshold else None)

    # JSONL mode
    if args.as_jsonl:
        out = sys.stdout
        for s in inputs:
            rec = {"text": s}
            for h, bundle in sessions.items():
                ort_inputs = {bundle.input_name: [[s]]}
                # Collect requested outputs
                fetches = [bundle.output_prob]
                res = bundle.session.run(fetches, ort_inputs)
                prob_val = res[0] if res else None
                # Extract probability
                p_uncal: float = 0.0
                if prob_val is not None:
                    try:
                        # Prefer positive class at index 1 if shape (1,2)
                        if hasattr(prob_val, "shape") and len(prob_val.shape) == 2 and prob_val.shape[1] >= 2:
                            p_uncal = float(prob_val[0, 1])
                        else:
                            p_uncal = float(np.ravel(prob_val)[0])
                    except Exception:
                        p_uncal = float(prob_val[0][0] if isinstance(prob_val, list) else prob_val)
                p = p_uncal
                params = bundle.calibration or {}
                if params:
                    try:
                        a = float(params.get("a", 1.0))
                        b = float(params.get("b", 0.0))
                        # Apply standard Platt scaling: convert to logit space first
                        logit = np.log(p_uncal / (1.0 - p_uncal + 1e-10))
                        p = float(1.0 / (1.0 + np.exp(-(a * logit + b))))
                    except Exception:
                        p = p_uncal
                if args.prob:
                    rec[f"p_{h}"] = p
                rec[h] = int(p >= thr_by_head[h])
            out.write(json.dumps(rec) + "\n")
        return 0

    # CSV mode
    writer = csv.writer(sys.stdout)
    heads = list(sessions.keys())
    headers = ["text"] + heads
    if args.prob:
        headers += [f"p_{h}" for h in heads]
    if not args.no_header:
        writer.writerow(headers)
    for s in inputs:
        preds: List[int] = []
        probs: List[float] = []
        for h, bundle in sessions.items():
            ort_inputs = {bundle.input_name: [[s]]}
            fetches = [bundle.output_prob]
            res = bundle.session.run(fetches, ort_inputs)
            prob_val = res[0] if res else None
            if prob_val is not None and hasattr(prob_val, "shape") and len(prob_val.shape) == 2 and prob_val.shape[1] >= 2:
                p_uncal = float(prob_val[0, 1])
            else:
                p_uncal = float(np.ravel(prob_val)[0]) if prob_val is not None else 0.0
            p = p_uncal
            params = bundle.calibration or {}
            if params:
                try:
                    a = float(params.get("a", 1.0))
                    b = float(params.get("b", 0.0))
                    p = float(1.0 / (1.0 + np.exp(-(a * p_uncal + b))))
                except Exception:
                    p = p_uncal
            preds.append(int(p >= thr_by_head[h]))
            probs.append(p)
        row: List[str | float | int] = [s] + preds
        if args.prob:
            row += [f"{p:.6f}" for p in probs]
        writer.writerow(row)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
