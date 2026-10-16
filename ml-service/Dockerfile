FROM python:3.11-slim

WORKDIR /srv
COPY requirements.txt .
RUN pip install --no-cache-dir --default-timeout=120 --retries 5 -r requirements.txt
COPY app ./app
RUN useradd --system --no-create-home app
ENV ML_MODEL_DIR=/srv/models
USER app
EXPOSE 8000
# DEPLOY-01: PORT ve bağlanma adresi env'den. Varsayılan 0.0.0.0 (IPv4): Railway'de 16.10.2025 sonrası ortamların private DNS'i
# IPv4 + IPv6 döndürür ve Java istemcisi IPv4'ü tercih eder. Yalnız IPv6 private ağlı (eski) ortamda ML_BIND_HOST="::" verilir —
# uvicorn "::" ile yalnız IPv6 dinler (yerelde ölçüldü), ikisi birden olmaz.
ENV PORT=8000 ML_BIND_HOST=0.0.0.0
CMD ["sh", "-c", "exec uvicorn app.main:app --host \"$ML_BIND_HOST\" --port \"$PORT\""]
