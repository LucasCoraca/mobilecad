# Headset-free OpenXR test bench: Monado (open-source OpenXR runtime) with its "qwerty" driver, which simulates an HMD
# and two controllers from keyboard + mouse, on a virtual X display with software rendering (Mesa llvmpipe / lavapipe).
# The display is shared over VNC and in the browser (noVNC on :6080). The MobileCAD XR desktop build runs against it.
FROM ubuntu:26.04
ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
      monado-service libopenxr1-monado libopenxr-loader1 libopenxr-dev openxr-layer-corevalidation \
      xvfb x11vnc novnc websockify openbox xdotool x11-utils x11-apps imagemagick \
      mesa-vulkan-drivers libgl1-mesa-dri libglx-mesa0 libegl-mesa0 mesa-utils vulkan-tools \
      libgl-dev libx11-dev cmake g++ make pkg-config ca-certificates \
    && rm -rf /var/lib/apt/lists/*
# software Vulkan/GL so it runs on any machine (no GPU passthrough needed)
ENV LIBGL_ALWAYS_SOFTWARE=1 VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/lvp_icd.json \
    XR_RUNTIME_JSON=/usr/share/openxr/1/openxr_monado.json DISPLAY=:1
COPY entrypoint.sh /usr/local/bin/xrsim-entry
COPY arrange.sh /usr/local/bin/xrsim-arrange
RUN chmod +x /usr/local/bin/xrsim-entry /usr/local/bin/xrsim-arrange
ENTRYPOINT ["/usr/local/bin/xrsim-entry"]
