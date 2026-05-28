#!/bin/bash

# CDN deployment script for the LangXianCheng Kiosk H5 landing page.
# Builds the project and syncs to CDN storage.
#
# Usage:
#   ./cdn-sync.sh [environment]
#
# Environments:
#   staging    — Deploy to staging CDN (default)
#   production — Deploy to production CDN

set -euo pipefail

# Configuration
PROJECT_DIR="$(cd "$(dirname "$0")/../" && pwd)"
BUILD_DIR="${PROJECT_DIR}/dist"
ENVIRONMENT="${1:-staging}"

# CDN configuration (update with actual values)
STAGING_CDN_BUCKET="s3://cdn-staging.langxiancheng.com/kiosk"
PRODUCTION_CDN_BUCKET="s3://cdn.langxiancheng.com/kiosk"
CDN_BUCKET=""

# Select CDN target based on environment
case "${ENVIRONMENT}" in
  staging)
    CDN_BUCKET="${STAGING_CDN_BUCKET}"
    ;;
  production)
    CDN_BUCKET="${PRODUCTION_CDN_BUCKET}"
    ;;
  *)
    echo "❌ Unknown environment: ${ENVIRONMENT}"
    echo "Usage: $0 [staging|production]"
    exit 1
    ;;
esac

echo "🚀 Deploying LangXianCheng Kiosk H5 to ${ENVIRONMENT}"
echo "   Project: ${PROJECT_DIR}"
echo "   CDN:     ${CDN_BUCKET}"
echo ""

# Step 1: Install dependencies
echo "📦 Installing dependencies..."
cd "${PROJECT_DIR}"
npm ci --production=false

# Step 2: Build project
echo "🔨 Building project..."
npm run build

# Step 3: Verify build output
if [ ! -d "${BUILD_DIR}" ]; then
  echo "❌ Build directory not found: ${BUILD_DIR}"
  exit 1
fi

BUILD_SIZE=$(du -sh "${BUILD_DIR}" | cut -f1)
echo "✅ Build complete (${BUILD_SIZE})"

# Step 4: Sync to CDN
echo "☁️  Syncing to CDN..."
if command -v aws &> /dev/null; then
  aws s3 sync "${BUILD_DIR}" "${CDN_BUCKET}" \
    --delete \
    --cache-control "max-age=300,s-maxage=86400" \
    --content-encoding "gzip" \
    --exclude "*.map"
  echo "✅ CDN sync complete"
else
  echo "⚠️  AWS CLI not found. Skipping CDN sync."
  echo "   Manual sync: aws s3 sync ${BUILD_DIR} ${CDN_BUCKET}"
fi

# Step 5: Invalidate CDN cache
if [ "${ENVIRONMENT}" = "production" ]; then
  echo "🔄 Invalidating CDN cache..."
  if command -v aws &> /dev/null; then
    DISTRIBUTION_ID=$(aws cloudfront list-distributions \
      --query "DistributionList.Items[?contains(Aliases.Items, 'cafe.langxiancheng.com')].Id" \
      --output text 2>/dev/null || echo "")

    if [ -n "${DISTRIBUTION_ID}" ]; then
      aws cloudfront create-invalidation \
        --distribution-id "${DISTRIBUTION_ID}" \
        --paths "/kiosk/*"
      echo "✅ Cache invalidation requested"
    else
      echo "⚠️  Could not find CloudFront distribution ID"
    fi
  fi
fi

echo ""
echo "🎉 Deployment to ${ENVIRONMENT} complete!"
echo "   URL: https://cafe.langxiancheng.com/result"
