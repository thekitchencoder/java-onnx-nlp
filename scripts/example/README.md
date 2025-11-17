# Example Training Data

## About This Data

This directory contains **synthetic training data** generated for testing and demonstration purposes.

### ✅ Safe to Commit

- **All data in this directory is completely synthetic**
- Contains **no real Personally Identifiable Information (PII)**
- Safe to version control and share publicly
- Used for model training examples and integration tests

### Files

- `training_data.csv` - Synthetic text samples with labels (address, voda, risk)
- `test_data.txt` - Sample texts for testing trained models

## ⚠️ WARNING: Do NOT Add Real Data

**NEVER commit files containing real PII to version control**, including:

- ❌ Real names, addresses, or contact information
- ❌ Medical records or health information
- ❌ Financial data or account numbers
- ❌ Government identifiers (SSN, passport numbers, etc.)
- ❌ Production data exports
- ❌ Customer communications or case notes

### Best Practices for Real Data

If working with real data for training or testing:

1. **Keep it local** - Store in a separate directory outside version control
2. **Use .gitignore** - Ensure sensitive directories/files are properly ignored
3. **Encrypt at rest** - Use encrypted storage for sensitive data
4. **Anonymize** - Strip PII before using for development
5. **Use synthetic data** - Generate fake data that mimics production patterns

## Data Generation

The synthetic data in this directory was created to simulate realistic text classification scenarios while containing no actual personal information. It can be safely used for:

- Training ONNX text classification models
- Running integration tests
- Demonstrating model capabilities
- Performance benchmarking

---

**Remember**: When in doubt, keep it out (of git)!
