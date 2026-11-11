name: Build ARM64 Launcher

on:
  push:
    branches: [ "main" ]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  build-windows-arm64:
    runs-on: windows-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Setup Node.js 20
        uses: actions/setup-node@v4
        with:
          node-version: 20

      - name: Install Dependencies
        run: npm install

      - name: Build ARM64 Executable
        run: npm run pack:arm

      - name: Publish to GitHub Releases
        uses: ncipollo/release-action@v1
        with:
          artifacts: "dist/*.exe"
          tag: "v1.0.0"
          allowUpdates: true
          token: ${{ secrets.GITHUB_TOKEN }}
