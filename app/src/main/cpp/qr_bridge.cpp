// JNI bridge between the Kotlin editor and the shared C++ core.
//
// The bridge owns no QR logic: every entry point parses the wire form into an
// app::AppInputs and then calls the same core functions the desktop app calls, so
// both front ends behave identically by construction. What lives here is only the
// translation, plus the promise that nothing unwinds across the JNI boundary.
//
// Text crosses as UTF-8 byte arrays, not as jstring, because JNI strings use
// modified UTF-8 and would mangle any payload with characters outside the BMP.

#include <jni.h>

#include <cstdint>
#include <exception>
#include <memory>
#include <stdexcept>
#include <string>
#include <vector>

#include "app/app_state.hpp"
#include "inputs_codec.hpp"

namespace {

std::string read_utf8(JNIEnv* env, jbyteArray array) {
  if (array == nullptr) throw std::runtime_error("entradas nulas");
  const jsize length = env->GetArrayLength(array);
  std::string text(static_cast<std::size_t>(length), '\0');
  if (length > 0) {
    env->GetByteArrayRegion(array, 0, length,
                            reinterpret_cast<jbyte*>(&text[0]));
  }
  return text;
}

jbyteArray write_utf8(JNIEnv* env, const std::string& text) {
  jbyteArray array = env->NewByteArray(static_cast<jsize>(text.size()));
  if (array == nullptr) return nullptr;
  if (!text.empty()) {
    env->SetByteArrayRegion(array, 0, static_cast<jsize>(text.size()),
                            reinterpret_cast<const jbyte*>(text.data()));
  }
  return array;
}

// Packs a raster image into the colour ints Bitmap expects: 0xAARRGGBB,
// straight (non-premultiplied) alpha, which is what the core emits.
//
// The bridge hands the pixels over instead of building the Bitmap itself: the
// image is then Android's own object, created by the SDK call in Kotlin, and this
// file needs no Android class lookup at all.
jintArray make_pixels(JNIEnv* env, const render::Image& image) {
  jintArray buffer = env->NewIntArray(image.width * image.height);
  if (buffer == nullptr) return nullptr;
  std::vector<jint> pixels(static_cast<std::size_t>(image.width) *
                           static_cast<std::size_t>(image.height));
  for (std::size_t i = 0; i < pixels.size(); ++i) {
    const std::size_t o = i * 4;
    const std::uint32_t argb = (static_cast<std::uint32_t>(image.rgba[o + 0]) << 24) |
                               (static_cast<std::uint32_t>(image.rgba[o + 1]) << 16) |
                               (static_cast<std::uint32_t>(image.rgba[o + 2]) << 8) | 0xFFu;
    pixels[i] = static_cast<jint>(argb);
  }
  env->SetIntArrayRegion(buffer, 0, static_cast<jsize>(pixels.size()), pixels.data());
  return buffer;
}

void throw_java(JNIEnv* env, const std::string& message) {
  jclass clazz = env->FindClass("java/lang/IllegalStateException");
  if (clazz != nullptr) env->ThrowNew(clazz, message.c_str());
}

// `inputs` plus the symbol it produced, for the calls that need both.
struct Session {
  app::AppInputs inputs;
  app::Generated generated;
};

std::unique_ptr<Session> verified_session(JNIEnv* env, jbyteArray inputs) {
  const std::string text = read_utf8(env, inputs);
  auto session = std::make_unique<Session>();
  std::string error;
  if (!bridge::parse_inputs(text, &session->inputs, &error)) {
    throw_java(env, error);
    return nullptr;
  }
  core::Result<app::Generated> result = app::generate(session->inputs);
  if (!result.has_value()) {
    throw_java(env, result.error().message);
    return nullptr;
  }
  session->generated = std::move(result.value());
  return session;
}

}  // namespace

extern "C" {

JNIEXPORT jbyteArray JNICALL
Java_com_disruptorh_qrgenerator_core_QrCore_nativeGenerate(JNIEnv* env, jobject, jbyteArray inputs) {
  try {
    const std::string text = read_utf8(env, inputs);
    auto parsed = std::make_unique<app::AppInputs>();
    std::string error;
    core::Result<app::Generated> result = bridge::parse_inputs(text, parsed.get(), &error)
                                              ? app::generate(*parsed)
                                              : core::Result<app::Generated>::err("codec", error);
    return write_utf8(env, bridge::build_record(*parsed, result));
  } catch (const std::exception& error) {
    throw_java(env, error.what());
    return nullptr;
  }
}

JNIEXPORT jobject JNICALL
Java_com_disruptorh_qrgenerator_core_QrCore_nativePreview(JNIEnv* env, jobject, jbyteArray inputs) {
  try {
    const std::unique_ptr<Session> session = verified_session(env, inputs);
    if (session == nullptr) return nullptr;
    // One pixel per module: the phone scales the bitmap, so the preview costs
    // almost nothing and comes from the same rasterizer as the exported file.
    // build_record() reports the matching size, and both are derived from the
    // same AppInputs snapshot, so Kotlin never has to guess it.
    app::AppInputs preview_inputs = session->inputs;
    preview_inputs.render.module_px = 1;
    const core::Result<render::Image> image =
        app::render_image(preview_inputs, session->generated);
    if (!image.has_value()) {
      throw_java(env, image.error().message);
      return nullptr;
    }
    return make_pixels(env, image.value());
  } catch (const std::exception& error) {
    throw_java(env, error.what());
    return nullptr;
  }
}

JNIEXPORT jbyteArray JNICALL
Java_com_disruptorh_qrgenerator_core_QrCore_nativeExportPng(JNIEnv* env, jobject,
                                                            jbyteArray inputs) {
  try {
    const std::unique_ptr<Session> session = verified_session(env, inputs);
    if (session == nullptr) return nullptr;
    const core::Result<std::vector<std::uint8_t>> png =
        app::export_png(session->inputs, session->generated);
    if (!png.has_value()) {
      throw_java(env, png.error().message);
      return nullptr;
    }
    const std::vector<std::uint8_t>& bytes = png.value();
    jbyteArray array = env->NewByteArray(static_cast<jsize>(bytes.size()));
    if (array == nullptr) return nullptr;
    if (!bytes.empty()) {
      env->SetByteArrayRegion(array, 0, static_cast<jsize>(bytes.size()),
                              reinterpret_cast<const jbyte*>(bytes.data()));
    }
    return array;
  } catch (const std::exception& error) {
    throw_java(env, error.what());
    return nullptr;
  }
}

JNIEXPORT jbyteArray JNICALL
Java_com_disruptorh_qrgenerator_core_QrCore_nativeExportSvg(JNIEnv* env, jobject,
                                                            jbyteArray inputs) {
  try {
    const std::unique_ptr<Session> session = verified_session(env, inputs);
    if (session == nullptr) return nullptr;
    const core::Result<std::string> svg =
        app::export_svg(session->inputs, session->generated);
    if (!svg.has_value()) {
      throw_java(env, svg.error().message);
      return nullptr;
    }
    return write_utf8(env, svg.value());
  } catch (const std::exception& error) {
    throw_java(env, error.what());
    return nullptr;
  }
}

}  // extern "C"
