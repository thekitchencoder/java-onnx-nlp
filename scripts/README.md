# NLP Classifier quickstart

Mini guide to run the standalone ONNX training and evaluation scripts.

## 1) Create and activate a virtual environment

```bash
python3 -m venv .venv
source .venv/bin/activate
python -m pip install --upgrade pip
```

Deactivate any time with `deactivate`.

## 2) Install dependencies

These standalone scripts do require a few Python packages.

Quick install, use the provided requirements file:

```bash
pip install -r requirements.txt
```

Notes:
- onnxruntime is optional if you only train/convert to ONNX, but it is REQUIRED to run the evaluation script (`onnx_eval.py`).
- Python 3.12 is recommended. Pre-compiled wheels for these packages are available for 3.9–3.12 on most platforms.

## 3) Run the script

Scripts should be run from the project folder

### Basic usage (label columns inferred by name `label_`):

```bash
python onnx_train.py example/training_data.csv
```

### Specify heads and output name explicitly:

```bash
python onnx_train.py example/training_data.csv \
  --label-col address --label-col risk \
  --name classifier
```

### With overrides JSON, custom opset, and ZipMap enabled:

```bash
python onnx_train.py example/training_data.csv \
  --config overrides.json --opset 13 --zipmap
```

The generated artifacts are written as ModelBundle directories under `<name>`:

```
<name>/
  <headA>/
    ├── model.onnx
    ├── config.json
    └── calibration.json   # optional; written only if not identity
  <headB>/
    ├── model.onnx
    ├── config.json
    └── calibration.json   # optional
```

### Overrides JSON examples (per-head) and calibration
**_UNTESTED FEATURE: overrides JSON support is experimental, it might not work as expected._**

You can pass a sparse per-head overrides JSON via `--config`. Keys must be the exact label column names in your CSV
(for example, this repo’s sample file `example/sample_training_data.csv` has columns `label_address`, `label_risk` and
`label_voda`). Any heads not present in the overrides file use defaults.

Supported per-head parameters (keys inside each head object):
- `strip_accents`: one of `None`, `"ascii"`, or `"unicode"` (default: `None`)
- `use_word_features`: boolean (default: `true`)
- `word_ngram_range`: two-element array, e.g. `[1, 2]` (default: `[1, 2]`)
- `word_min_df`: integer min document frequency (default: `1`)
- `C`: float regularization strength for LogisticRegression (default: `0.5`)

Note: Global export options like opset or ZipMap are CLI flags (`--opset`, `--zipmap`), not part of the JSON.

#### Example A — Minimal overrides for the sample dataset

Given `address_training_data.csv` containing heads `label_address` and `label_risk`, put this into `overrides.json` in
the project root:

```json
{
  "label_address": {
    "word_ngram_range": [1, 2],
    "word_min_df": 2
  },
  "label_risk": {
    "C": 0.8,
    "strip_accents": "unicode"
  }
}
```

Run with explicit heads and a custom name:

```bash
python onnx_train.py example/training_data.csv \
  --label-col address --label-col risk \
  --name o1 \
  --config overrides.json \
  --opset 13 --zipmap
```

This will produce per-head ModelBundle directories under `o1/`, for example:
- `o1/address/{model.onnx, config.json, calibration.json?}`
- `o1/risk/{model.onnx, config.json, calibration.json?}`

#### Example B — Override one head, leave the other at defaults

```json
{
  "label_risk": {
    "C": 1.2,
    "word_ngram_range": [1, 1]
  }
}
```

```bash
python onnx_train.py example/training_data.csv \
  --label-col address --label-col risk \
  --name classifier \
  --config overrides.json
```

Here only the `risk` head changes; `address` uses default parameters.

#### Example C — Turn on ZipMap via CLI and keep JSON small

ZipMap is controlled by `--zipmap` (not by JSON):

```bash
python onnx_train.py example/training_data.csv \
  --label-col address --label-col risk \
  --name eag \
  --config overrides.json \
  --zipmap
```

#### Calibration per head

Each head is calibrated independently using Platt scaling on the training fold, fitting a sigmoid `1/(1+exp(-(a*x+b)))`
to map raw probabilities to calibrated probabilities. The fitted parameters are saved as `<name>/<head>/calibration.json` with fields
`calibrationType` ("platt" or "identity") and nested `parameters` containing `a` and `b`.

At inference time, the evaluator automatically applies the calibration found in each head directory.

## 3b) Run the evaluation script

Evaluate one or more exported ONNX models on input texts. Inputs can be passed as a single positional argument or
streamed via stdin (one line per input). Output defaults to JSON Lines; CSV is supported.

Basic usage with directory discovery by name:

```bash
# Uses all ModelBundle subdirectories under <name>
python onnx_eval.py --name classifier "the free form text"
```

Read multiple inputs from stdin and output CSV with thresholds and no header:

```bash
cat example/test_data.txt | \
  python onnx_eval.py --name classifier \
  --csv --no-prob \
  --threshold address=0.6 --threshold risk=0.4
```

**Notes**:
- CSV headers are included by default. Pass `--no-header` to omit them.
- Probabilities are included by default. Pass `--no-prob` to omit them.
- If calibration JSON files are present (e.g., `<name>/<head>/calibration.json`), calibrated probabilities are computed via Platt scaling.
- Head names are inferred from subdirectory names under `<name>`.

## 4) Troubleshooting

- macOS Apple Silicon: ensure you have recent Python and pip (`python -m pip install --upgrade pip`). Wheels are available; no compiler should be needed.
- CSV must contain a `text` column and either explicit `--label-col` values or columns named like `label_<head>`.
- If conversion to ONNX fails, confirm `skl2onnx` is installed and try a lower `--opset` (e.g., 13).
