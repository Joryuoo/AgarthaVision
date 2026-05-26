# Inference Server Test Commands

## 1. Provision and start the server

```bash
ssh root@<droplet-ip>
```
Prepare your Github Personal Access Token
```bash
sudo apt-get update && sudo apt-get upgrade
docker login ghcr.io
```

```bash
docker run --device=/dev/kfd --device=/dev/dri --group-add video \
  -p 8000:8000 -d \
  -e INFERENCE_API_KEY="<your-secret>" \
  -e MODEL_VERSION="yolov26-efficientnetv2-v1" \
  ghcr.io/dmkuzu/agartha-inference:v2
```

Exit SSH after the container starts.

## 2. Health check

```bash
curl http://<droplet-ip>:8000/health
```

Expected: `{"status":"ok"}`

## 3. Inference

```bash
curl -X POST \
  -H "Authorization: Bearer <your-secret>" \
  -H "Content-Type: image/jpeg" \
  --data-binary "@/home/kuzu/Documents/school/AgarthaVision_Dataset/dataset/test/images/A. lumbricoides (Decorticated egg) (1).jpg" \
  http://<droplet-ip>:8000/infer
```

Expected:
```json
{
  "model_version": "yolov26-efficientnetv2-v1",
  "predictions": [
    {"class": "Ascaris lumbricoides", "confidence": 0.96, "x": 2501.32, "y": 1686.82, "width": 800.79, "height": 793.18}
  ],
  "image": {"width": 5184, "height": 3456}
}
```

Empty when nothing detected: `{"model_version": "yolov26-efficientnetv2-v1", "predictions": [], "image": {"width": ..., "height": ...}}`

## 4. Destroy the droplet when done

~$1.99/hr — destroy after every session.
