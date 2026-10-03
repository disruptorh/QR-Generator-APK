// Checks the wire format the Kotlin editor speaks against the C++ core.
//
// The messages below are written by hand as the Kotlin side would send them,
// including the percent-escapes, so a change to either side that breaks the
// format shows up here.

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <string>
#include <vector>

#include "app/app_state.hpp"
#include "inputs_codec.hpp"

namespace {

int g_failures = 0;

void check(bool condition, const std::string& what) {
  if (!condition) {
    std::printf("FALLO: %s\n", what.c_str());
    ++g_failures;
  }
}

void check_equal(const std::string& actual, const std::string& expected, const std::string& what) {
  if (actual == expected) return;
  std::printf("FALLO: %s\n", what.c_str());
  ++g_failures;
}

void check_equal(long actual, long expected, const std::string& what) {
  if (actual == expected) return;
  std::printf("FALLO: %s (obtenido %ld, esperado %ld)\n", what.c_str(), actual, expected);
  ++g_failures;
}

std::string field_of(const std::string& record, const std::string& key) {
  const std::string prefix = key + "=";
  std::size_t start = 0;
  while (start <= record.size()) {
    std::size_t end = record.find('\n', start);
    if (end == std::string::npos) end = record.size();
    const std::string line = record.substr(start, end - start);
    if (line.rfind(prefix, 0) == 0) return bridge::unescape(line.substr(prefix.size()));
    if (end == record.size()) break;
    start = end + 1;
  }
  return std::string();
}

void test_escape_round_trip() {
  const std::vector<std::string> samples = {
      "",
      "texto normal",
      "con\nsaltos\nde linea",
      "con=igual",
      "100%",
      "%2G sin pareja",
      "acentos: \xC3\xA1\xC3\xA9\xC3\xAD\xC3\xB3 \xC3\xBC",
      "emoji \xF0\x9F\x98\x80",
      "WIFI:T:WPA;S:Mi Red;P:se;cre:to;;",
  };
  for (const std::string& sample : samples) {
    const std::string escaped = bridge::escape(sample);
    check(escaped.find('\n') == std::string::npos, "el escape no debe dejar saltos de linea");
    check(escaped.find('=') == std::string::npos, "el escape no debe dejar signos igual");
    check_equal(bridge::unescape(escaped), sample, "ida y vuelta de escape: " + sample);
  }

  // Bytes outside the plain set are written as %XX with upper-case hex.
  check_equal(bridge::escape("a\n"), "a%0A", "escape de salto de linea");
  check_equal(bridge::escape("\xC3\xB1"), "%C3%B1", "escape de byte UTF-8");
  check_equal(bridge::escape("a-b_c.d~e"), "a-b_c.d~e", "los caracteres simples no se escapan");

  // A '%' that is not two hex digits is literal text, not a dropped character.
  check_equal(bridge::unescape("100%"), "100%", "escape incompleto al final");
  check_equal(bridge::unescape("50%2"), "50%2", "escape incompleto con un digito");
  check_equal(bridge::unescape("%%41"), "%A", "escape doble conserva el resto");
}

void test_parse_full_message() {
  const std::string message =
      "type=vcard\n"
      "t.body=cuerpo\n"
      "url=https%3A%2F%2Fejemplo.es\n"
      "wifi.ssid=Red%20de%20prueba\n"
      "wifi.pass=clave%3Bcon%3Bpunto%3A\n"
      "wifi.auth=sae\n"
      "wifi.hidden=1\n"
      "vc.name=Ana%20Ruiz\n"
      "vc.org=Acme\n"
      "vc.title=Ingeniera\n"
      "vc.phone=600000001\n"
      "vc.phone=600000002\n"
      "vc.email=ana%40ejemplo.es\n"
      "vc.url=https%3A%2F%2Fana.es\n"
      "vc.addr=Calle%20Mayor%201%0A28013%20Madrid\n"
      "em.addr=ana%40ejemplo.es\n"
      "em.subject=Hola\n"
      "em.body=Cuerpo%20del%20correo\n"
      "sms.number=600000003\n"
      "sms.message=Hola%0Aque%20tal\n"
      "tel.number=600000004\n"
      "geo.lat=40.416800\n"
      "geo.lon=-3.703800\n"
      "geo.alt=650\n"
      "ev.summary=Reunion\n"
      "ev.start=2026-01-15T10%3A00\n"
      "ev.end=2026-01-15T11%3A00\n"
      "ev.location=Sala%201\n"
      "ev.desc=Orden%20del%20dia\n"
      "enc.ecc=quartile\n"
      "enc.boost=0\n"
      "enc.mask=3\n"
      "enc.minver=2\n"
      "enc.maxver=12\n"
      "rnd.module_px=12\n"
      "rnd.quiet=6\n"
      "rnd.fg=1A2B3C\n"
      "rnd.bg=FEDCBA\n"
      "out.format=svg\n";

  app::AppInputs inputs;
  std::string error;
  check(bridge::parse_inputs(message, &inputs, &error), "el mensaje completo debe parsear: " + error);
  check(error.empty(), "el mensaje completo no debe reportar error");

  check(inputs.type == payload::ContentType::vcard, "tipo vcard");
  check_equal(inputs.text.body, std::string("cuerpo"), "t.body");
  check_equal(inputs.url.url, std::string("https://ejemplo.es"), "url");
  check_equal(inputs.wifi.ssid, std::string("Red de prueba"), "wifi.ssid");
  check_equal(inputs.wifi.password, std::string("clave;con;punto:"), "wifi.pass");
  check(inputs.wifi.auth == payload::WifiAuth::sae, "wifi.auth");
  check(inputs.wifi.hidden, "wifi.hidden");
  check_equal(inputs.contact.full_name, std::string("Ana Ruiz"), "vc.name");
  check_equal(inputs.contact.organization, std::string("Acme"), "vc.org");
  check_equal(inputs.contact.title, std::string("Ingeniera"), "vc.title");
  check_equal(inputs.contact.phones.size(), std::size_t(2), "numero de telefonos");
  check_equal(inputs.contact.phones[0], std::string("600000001"), "primer telefono");
  check_equal(inputs.contact.phones[1], std::string("600000002"), "segundo telefono");
  check_equal(inputs.contact.emails.size(), std::size_t(1), "numero de correos");
  check_equal(inputs.contact.emails[0], std::string("ana@ejemplo.es"), "correo");
  check_equal(inputs.contact.url, std::string("https://ana.es"), "vc.url");
  check_equal(inputs.contact.address, std::string("Calle Mayor 1\n28013 Madrid"), "vc.addr");
  check_equal(inputs.email.address, std::string("ana@ejemplo.es"), "em.addr");
  check_equal(inputs.email.subject, std::string("Hola"), "em.subject");
  check_equal(inputs.email.body, std::string("Cuerpo del correo"), "em.body");
  check_equal(inputs.sms.number, std::string("600000003"), "sms.number");
  check_equal(inputs.sms.message, std::string("Hola\nque tal"), "sms.message");
  check_equal(inputs.phone.number, std::string("600000004"), "tel.number");
  check(inputs.geo.latitude == 40.4168, "geo.lat");
  check(inputs.geo.longitude == -3.7038, "geo.lon");
  check_equal(inputs.geo.altitude, std::string("650"), "geo.alt");
  check_equal(inputs.event.summary, std::string("Reunion"), "ev.summary");
  check_equal(inputs.event.start_local, std::string("2026-01-15T10:00"), "ev.start");
  check_equal(inputs.event.end_local, std::string("2026-01-15T11:00"), "ev.end");
  check_equal(inputs.event.location, std::string("Sala 1"), "ev.location");
  check_equal(inputs.event.description, std::string("Orden del dia"), "ev.desc");
  check(inputs.encode.ecc == qr::Ecc::quartile, "enc.ecc");
  check(!inputs.encode.boost_ecc, "enc.boost");
  check_equal(inputs.encode.mask, 3, "enc.mask");
  check_equal(inputs.encode.min_version, 2, "enc.minver");
  check_equal(inputs.encode.max_version, 12, "enc.maxver");
  check_equal(inputs.render.module_px, 12, "rnd.module_px");
  check_equal(inputs.render.quiet_zone, 6, "rnd.quiet");
  check_equal(int(inputs.render.foreground.r), 0x1A, "rnd.fg rojo");
  check_equal(int(inputs.render.foreground.g), 0x2B, "rnd.fg verde");
  check_equal(int(inputs.render.foreground.b), 0x3C, "rnd.fg azul");
  check_equal(int(inputs.render.background.r), 0xFE, "rnd.bg rojo");
  check_equal(int(inputs.render.background.g), 0xDC, "rnd.bg verde");
  check_equal(int(inputs.render.background.b), 0xBA, "rnd.bg azul");
  check(inputs.format == app::OutputFormat::svg, "out.format");
}

void test_defaults_and_unknown_keys() {
  app::AppInputs inputs;
  std::string error;
  // No message at all keeps every core default.
  check(bridge::parse_inputs("", &inputs, &error), "un mensaje vacio es valido");
  check(inputs.type == payload::ContentType::text, "tipo por defecto");
  check(inputs.encode.ecc == qr::Ecc::medium, "correccion por defecto");
  check(inputs.encode.boost_ecc, "refuerzo por defecto");
  check_equal(inputs.encode.mask, -1, "mascara automatica por defecto");
  check_equal(inputs.encode.min_version, 1, "version minima por defecto");
  check_equal(inputs.encode.max_version, 40, "version maxima por defecto");
  check_equal(inputs.render.module_px, 8, "px por modulo por defecto");
  check_equal(inputs.render.quiet_zone, 4, "zona de silencio por defecto");
  check(inputs.format == app::OutputFormat::png, "formato por defecto");

  // An unknown key is dropped instead of failing, so the format can grow.
  const std::string message =
      "type=url\n"
      "clave.desconocida=lo%20que%20sea\n"
      "linea%20sin%20igual\n"
      "url=ejemplo.es\n";
  check(bridge::parse_inputs(message, &inputs, &error), "las claves desconocidas se ignoran: " + error);
  check_equal(inputs.url.url, std::string("ejemplo.es"), "url sigue presente");
}

void test_rejects_bad_values() {
  struct Case {
    const char* message;
    const char* key;
  };
  const Case cases[] = {
      {"type=codigo\n", "type"},
      {"wifi.auth=rot13\n", "wifi.auth"},
      {"enc.ecc=altisimo\n", "enc.ecc"},
      {"enc.mask=siete\n", "enc.mask"},
      {"enc.minver=uno\n", "enc.minver"},
      {"rnd.module_px=grande\n", "rnd.module_px"},
      {"rnd.quiet=-muchos\n", "rnd.quiet"},
      {"wifi.hidden=quiza\n", "wifi.hidden"},
      {"enc.boost=quiza\n", "enc.boost"},
      {"rnd.fg=12345\n", "rnd.fg"},
      {"rnd.bg=GGGGGG\n", "rnd.bg"},
      {"out.format=tiff\n", "out.format"},
      {"rnd.fg=\n", "rnd.fg"},
  };
  for (const Case& test : cases) {
    app::AppInputs inputs;
    std::string error;
    const bool ok = bridge::parse_inputs(test.message, &inputs, &error);
    check(!ok, std::string("debe rechazar: ") + test.message);
    check(error.rfind(test.key, 0) == 0,
          std::string("el error debe nombrar la clave ") + test.key + ", dio: " + error);
  }
}

void test_generate_end_to_end() {
  app::AppInputs inputs;
  std::string error;
  const std::string message =
      "type=wifi\n"
      "wifi.ssid=Caf%C3%A9%20Central\n"
      "wifi.pass=se%3Bcreto%3A\n"
      "wifi.auth=wpa\n"
      "wifi.hidden=1\n"
      "enc.ecc=high\n"
      "rnd.module_px=4\n"
      "rnd.quiet=4\n";
  check(bridge::parse_inputs(message, &inputs, &error), "el mensaje wifi debe parsear: " + error);

  const core::Result<app::Generated> result = app::generate(inputs);
  check(result.has_value(), "un wifi valido debe generar");
  if (!result.has_value()) return;
  check_equal(result.value().payload,
              std::string("WIFI:T:WPA;S:Caf\xC3\xA9 Central;P:se\\;creto\\:;H:true;;"),
              "payload wifi construido por el core");
  check(result.value().verify_stats.blocks > 0, "el simbolo debe verificarse");
  check(result.value().encode_stats.version >= 1, "version valida");

  const std::string record = bridge::build_record(inputs, result);
  check_equal(field_of(record, "ok"), std::string("1"), "ok en el registro correcto");
  check_equal(field_of(record, "payload"), result.value().payload, "payload en el registro");
  check_equal(field_of(record, "describe"), app::describe(result.value()), "describe en el registro");
  check_equal(field_of(record, "filename"), app::suggested_filename(inputs, result.value()),
              "filename en el registro");
  check_equal(field_of(record, "version"),
              std::to_string(result.value().encode_stats.version), "version en el registro");
  check_equal(field_of(record, "px_w"), std::to_string(4 * (result.value().matrix.size + 8)),
                "export width comes from the core rasterizer");
    // The preview is the same rasterizer at one pixel per module: the Kotlin side
    // needs those numbers to wrap the returned pixels in a Bitmap.
    {
      app::AppInputs one_pixel = inputs;
      one_pixel.render.module_px = 1;
      int expected_width = 0;
      int expected_height = 0;
      render::raster_size(result.value().matrix, one_pixel.render, &expected_width,
                          &expected_height);
      check_equal(field_of(record, "preview_w"), std::to_string(expected_width),
                  "preview width agrees with render_image()");
      check_equal(field_of(record, "preview_h"), std::to_string(expected_height),
                  "preview height agrees with render_image()");
    }
}

void test_preview_pixels_match_the_matrix() {
  app::AppInputs inputs;
  inputs.text.body = "Hola mundo";
  const core::Result<app::Generated> generated = app::generate(inputs);
  check(generated.has_value(), "generate succeeds");
  if (!generated.has_value()) return;

  app::AppInputs preview_inputs = inputs;
  preview_inputs.render.module_px = 1;
  const core::Result<render::Image> image = app::render_image(preview_inputs, generated.value());
  check(image.has_value(), "preview render succeeds");
  if (!image.has_value()) return;

  const render::Image& picture = image.value();
  int expected_width = 0;
  int expected_height = 0;
  render::raster_size(generated.value().matrix, preview_inputs.render, &expected_width,
                      &expected_height);
  check_equal(picture.width, expected_width, "preview width matches the core rasterizer");
  check_equal(picture.height, expected_height, "preview height matches the core rasterizer");

  // One pixel per module means the raster is the quiet zone plus the matrix, so
  // the phone can check every module of the symbol against its own pixels.
  const int quiet = preview_inputs.render.quiet_zone;
  const qr::Matrix& matrix = generated.value().matrix;
  int mismatches = 0;
  for (int y = 0; y < static_cast<int>(matrix.size); ++y) {
    for (int x = 0; x < static_cast<int>(matrix.size); ++x) {
      const std::size_t offset =
          (static_cast<std::size_t>(y + quiet) * picture.width + (x + quiet)) * 4;
      if (matrix.at(x, y) != (picture.rgba[offset] < 128)) ++mismatches;
    }
  }
  check_equal(mismatches, 0, "every module of the matrix matches its pixel");

  // The quiet zone stays white and opaque, otherwise scanners lose the symbol.
  bool corner_ok = picture.rgba[3] == 255;
  for (int x = 0; x < picture.width && corner_ok; ++x) {
    if (picture.rgba[static_cast<std::size_t>(x) * 4] != 255) corner_ok = false;
  }
  check(corner_ok, "the quiet zone is opaque white");
}

void test_record_reports_failure() {
  app::AppInputs inputs;
  std::string error;
  check(bridge::parse_inputs("type=wifi\nwifi.ssid=\n", &inputs, &error), "parsea");

  const core::Result<app::Generated> result = app::generate(inputs);
  check(!result.has_value(), "un wifi sin SSID debe fallar");
  if (result.has_value()) return;

  const std::string record = bridge::build_record(inputs, result);
  check_equal(field_of(record, "ok"), std::string("0"), "ok=0 en el registro con error");
  check_equal(field_of(record, "err.field"), result.error().field, "campo del error en el registro");
  check_equal(field_of(record, "err.msg"), result.error().message, "mensaje del error en el registro");
  check(field_of(record, "payload").empty(), "un registro con error no lleva payload");
}

void test_core_reports_the_bad_coordinate() {
  app::AppInputs inputs;
  std::string error;
  // A latitude the user is still typing arrives as text that is not a number.
  // It must reach the core as "not a number", not as a silently chosen value.
  check(bridge::parse_inputs("type=geo\ngeo.lat=cuarenta\ngeo.lon=0\n", &inputs, &error),
        "parsea: " + error);
  check(std::isnan(inputs.geo.latitude), "una latitud ilegible queda como NaN");

  const core::Result<app::Generated> result = app::generate(inputs);
  check(!result.has_value(), "una latitud ilegible debe fallar");
  if (result.has_value()) return;
  check_equal(result.error().field, std::string("latitude"),
              "el core senala el campo de latitud");
}

void test_exports_from_a_verified_symbol() {
  app::AppInputs inputs;
  std::string error;
  check(bridge::parse_inputs("type=text\nt.body=Hola%20mundo\nrnd.module_px=2\nrnd.quiet=2\n",
                             &inputs, &error),
        "parsea: " + error);
  const core::Result<app::Generated> result = app::generate(inputs);
  check(result.has_value(), "un texto simple debe generar");
  if (!result.has_value()) return;

  const core::Result<std::vector<std::uint8_t>> png = app::export_png(inputs, result.value());
  check(png.has_value(), "el png debe exportarse");
  if (png.has_value()) {
    // PNG signature; the same bytes the Android app writes to disk.
    const std::vector<std::uint8_t> signature = {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    check(png.value().size() > signature.size(), "el png tiene contenido");
    check(std::equal(signature.begin(), signature.end(), png.value().begin()),
          "el png empieza con su firma");
  }

  const core::Result<std::string> svg = app::export_svg(inputs, result.value());
  check(svg.has_value(), "el svg debe exportarse");
  if (svg.has_value()) {
    check(svg.value().rfind("<svg", 0) == 0, "el svg empieza por el elemento raiz");
  }
}

}  // namespace

int main() {
  test_escape_round_trip();
  test_parse_full_message();
  test_defaults_and_unknown_keys();
  test_rejects_bad_values();
  test_generate_end_to_end();
  test_preview_pixels_match_the_matrix();
  test_record_reports_failure();
  test_core_reports_the_bad_coordinate();
  test_exports_from_a_verified_symbol();

  if (g_failures == 0) {
    std::printf("codec_tests: todo correcto\n");
    return 0;
  }
  std::printf("codec_tests: %d fallos\n", g_failures);
  return 1;
}
