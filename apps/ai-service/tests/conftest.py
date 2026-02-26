from __future__ import annotations

import os


os.environ.setdefault("PY_ENV_PROVIDER", "conda")
os.environ.setdefault("PY_ENV_NAME", "edunexus-ai")
os.environ.setdefault("PYTHON_RUNNER", "uv")
os.environ.setdefault("CONDA_DEFAULT_ENV", "edunexus-ai")
