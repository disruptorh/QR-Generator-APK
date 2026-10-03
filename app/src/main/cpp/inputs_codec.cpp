#include "inputs_codec.hpp"

#include <cerrno>
#include <cmath>
#include <cstdint>
#include <cstdlib>
#include <string>
#include <vector>

namespace bridge {
namespace {

// One split line. `key` and `value` are still escaped.
struct Field {
  std::string key;
  std::string value;
};

std::vector<Field> split_fields(const std::string& text) {
  std::vector<Field> fields;
  std::size_t start = 0;
  while (start <= text.size()) {
    std::size_t end = text.find('\n', start);
    if (end == std::string::npos) end = text.size();
    std::string line = text.substr(start, end - start);
    if (!line.empty() && line.back() == '\r') line.pop_back();
    const std::size_t eq = line.find('=');
    // A line without '=' cannot be a field. Skipping it keeps one malformed line
    // from taking down every other field the editor sent.
    if (eq != std::string::npos) {
      fields.push_back(Field{line.substr(0, eq), line.substr(eq + 1)});
    }
    if (end == text.size()) break;
    start = end + 1;
  }
  return fields;
}

int hex_value(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  return -1;
}

bool parse_long(const std::string& text, long* out) {
  if (text.empty()) return false;
  errno = 0;
  char* end = nullptr;
  const long value = std::strtol(text.c_str(), &end, 10);
  if (errno != 0 || end == nullptr || *end != '\0') return false;
  *out = value;
  return true;
}

bool parse_double(const std::string& text, double* out) {
  if (text.empty()) return false;
  errno = 0;
  char* end = nullptr;
  const double value = std::strtod(text.c_str(), &end);
  if (end == nullptr || *end != '\0') return false;
  *out = value;
  return true;
}

bool parse_bool(const std::string& text, bool* out) {
  if (text == "1") {
    *out = true;
    return true;
  }
  if (text == "0") {
    *out = false;
    return true;
  }
  return false;
}

bool parse_colour(const std::string& text, render::Rgb* out) {
  if (text.size() != 6) return false;
  int channels[3] = {0, 0, 0};
  for (int i = 0; i < 3; ++i) {
    const int hi = hex_value(text[static_cast<std::size_t>(i) * 2]);
    const int lo = hex_value(text[static_cast<std::size_t>(i) * 2 + 1]);
    if (hi < 0 || lo < 0) return false;
    channels[i] = hi * 16 + lo;
  }
  out->r = static_cast<std::uint8_t>(channels[0]);
  out->g = static_cast<std::uint8_t>(channels[1]);
  out->b = static_cast<std::uint8_t>(channels[2]);
  return true;
}

// Applies one field. Returns false only for a recognised key whose value cannot
// be read; unknown keys are accepted and dropped, so the format can grow keys
// without breaking an older build.
bool apply_field(const Field& field, app::AppInputs* out, std::string* error) {
  const std::string& key = field.key;
  const std::string value = unescape(field.value);
  long number = 0;
  double real = 0.0;
  bool flag = false;

  if (key == "type") {
    if (value == "text") out->type = payload::ContentType::text;
    else if (value == "url") out->type = payload::ContentType::url;
    else if (value == "wifi") out->type = payload::ContentType::wifi;
    else if (value == "vcard") out->type = payload::ContentType::vcard;
    else if (value == "email") out->type = payload::ContentType::email;
    else if (value == "sms") out->type = payload::ContentType::sms;
    else if (value == "phone") out->type = payload::ContentType::phone;
    else if (value == "geo") out->type = payload::ContentType::geo;
    else if (value == "event") out->type = payload::ContentType::event;
    else *error = "tipo de contenido desconocido: " + value;
  } else if (key == "t.body") {
    out->text.body = value;
  } else if (key == "url") {
    out->url.url = value;
  } else if (key == "wifi.ssid") {
    out->wifi.ssid = value;
  } else if (key == "wifi.pass") {
    out->wifi.password = value;
  } else if (key == "wifi.auth") {
    if (value == "wpa") out->wifi.auth = payload::WifiAuth::wpa;
    else if (value == "sae") out->wifi.auth = payload::WifiAuth::sae;
    else if (value == "wep") out->wifi.auth = payload::WifiAuth::wep;
    else if (value == "nopass") out->wifi.auth = payload::WifiAuth::nopass;
    else *error = "seguridad desconocida: " + value;
  } else if (key == "wifi.hidden") {
    if (!parse_bool(value, &flag)) *error = "valor booleano invalido en wifi.hidden";
    else out->wifi.hidden = flag;
  } else if (key == "vc.name") {
    out->contact.full_name = value;
  } else if (key == "vc.org") {
    out->contact.organization = value;
  } else if (key == "vc.title") {
    out->contact.title = value;
  } else if (key == "vc.phone") {
    // Repeated keys append, which is how a list survives the flat format.
    out->contact.phones.push_back(value);
  } else if (key == "vc.email") {
    out->contact.emails.push_back(value);
  } else if (key == "vc.url") {
    out->contact.url = value;
  } else if (key == "vc.addr") {
    out->contact.address = value;
  } else if (key == "em.addr") {
    out->email.address = value;
  } else if (key == "em.subject") {
    out->email.subject = value;
  } else if (key == "em.body") {
    out->email.body = value;
  } else if (key == "sms.number") {
    out->sms.number = value;
  } else if (key == "sms.message") {
    out->sms.message = value;
  } else if (key == "tel.number") {
    out->phone.number = value;
  } else if (key == "geo.lat") {
    // A value the core cannot read stays NaN, so the range check in the payload
    // builder reports it as a coordinate error instead of the bridge inventing
    // a number the user did not type.
    if (!parse_double(value, &real)) real = std::nan("");
    out->geo.latitude = real;
  } else if (key == "geo.lon") {
    if (!parse_double(value, &real)) real = std::nan("");
    out->geo.longitude = real;
  } else if (key == "geo.alt") {
    out->geo.altitude = value;
  } else if (key == "ev.summary") {
    out->event.summary = value;
  } else if (key == "ev.start") {
    out->event.start_local = value;
  } else if (key == "ev.end") {
    out->event.end_local = value;
  } else if (key == "ev.location") {
    out->event.location = value;
  } else if (key == "ev.desc") {
    out->event.description = value;
  } else if (key == "enc.ecc") {
    if (value == "low") out->encode.ecc = qr::Ecc::low;
    else if (value == "medium") out->encode.ecc = qr::Ecc::medium;
    else if (value == "quartile") out->encode.ecc = qr::Ecc::quartile;
    else if (value == "high") out->encode.ecc = qr::Ecc::high;
    else *error = "correccion de errores desconocida: " + value;
  } else if (key == "enc.boost") {
    if (!parse_bool(value, &flag)) *error = "valor booleano invalido en enc.boost";
    else out->encode.boost_ecc = flag;
  } else if (key == "enc.mask") {
    if (!parse_long(value, &number)) *error = "mascara no numerica";
    else out->encode.mask = static_cast<int>(number);
  } else if (key == "enc.minver") {
    if (!parse_long(value, &number)) *error = "version minima no numerica";
    else out->encode.min_version = static_cast<int>(number);
  } else if (key == "enc.maxver") {
    if (!parse_long(value, &number)) *error = "version maxima no numerica";
    else out->encode.max_version = static_cast<int>(number);
  } else if (key == "rnd.module_px") {
    if (!parse_long(value, &number)) *error = "px por modulo no numerico";
    else out->render.module_px = static_cast<int>(number);
  } else if (key == "rnd.quiet") {
    if (!parse_long(value, &number)) *error = "zona de silencio no numerica";
    else out->render.quiet_zone = static_cast<int>(number);
  } else if (key == "rnd.fg") {
    if (!parse_colour(value, &out->render.foreground)) *error = "color de modulo invalido";
  } else if (key == "rnd.bg") {
    if (!parse_colour(value, &out->render.background)) *error = "color de fondo invalido";
  } else if (key == "out.format") {
    if (value == "png") out->format = app::OutputFormat::png;
    else if (value == "svg") out->format = app::OutputFormat::svg;
    else *error = "formato de salida desconocido: " + value;
  }
  return error->empty();
}

void write_field(std::string* out, const char* key, const std::string& value) {
  out->append(key);
  out->push_back('=');
  out->append(escape(value));
  out->push_back('\n');
}

void write_number(std::string* out, const char* key, long value) {
  out->append(key);
  out->push_back('=');
  out->append(std::to_string(value));
  out->push_back('\n');
}

}  // namespace

bool is_plain(unsigned char c) {
  return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' ||
         c == '_' || c == '~' || c == '-';
}

std::string escape(const std::string& value) {
  static const char kHex[] = "0123456789ABCDEF";
  std::string out;
  out.reserve(value.size() + 8);
  for (const char raw : value) {
    const unsigned char c = static_cast<unsigned char>(raw);
    if (is_plain(c)) {
      out.push_back(raw);
      continue;
    }
    out.push_back('%');
    out.push_back(kHex[c >> 4]);
    out.push_back(kHex[c & 0x0F]);
  }
  return out;
}

std::string unescape(const std::string& value) {
  std::string out;
  out.reserve(value.size());
  for (std::size_t i = 0; i < value.size(); ++i) {
    if (value[i] != '%' || i + 2 >= value.size()) {
      out.push_back(value[i]);
      continue;
    }
    const int hi = hex_value(value[i + 1]);
    const int lo = hex_value(value[i + 2]);
    if (hi < 0 || lo < 0) {
      out.push_back(value[i]);
      continue;
    }
    out.push_back(static_cast<char>(hi * 16 + lo));
    i += 2;
  }
  return out;
}

bool parse_inputs(const std::string& text, app::AppInputs* out, std::string* error) {
  *out = app::AppInputs{};
  error->clear();
  for (const Field& field : split_fields(text)) {
    if (!apply_field(field, out, error)) {
      error->insert(0, field.key + ": ");
      return false;
    }
  }
  return true;
}

std::string build_record(const app::AppInputs& inputs,
                         const core::Result<app::Generated>& result) {
  std::string out;
  if (!result.has_value()) {
    write_field(&out, "ok", "0");
    write_field(&out, "err.field", result.error().field);
    write_field(&out, "err.msg", result.error().message);
    return out;
  }

  const app::Generated& generated = result.value();
  const qr::EncodeStats& stats = generated.encode_stats;

  int width = 0;
  int height = 0;
  render::raster_size(generated.matrix, inputs.render, &width, &height);

  // The preview is the same rasterizer at one pixel per module, so the phone can
  // scale it for free. Its size belongs to the core too, not to the editor.
  render::RenderOptions preview_options = inputs.render;
  preview_options.module_px = 1;
  int preview_width = 0;
  int preview_height = 0;
  render::raster_size(generated.matrix, preview_options, &preview_width, &preview_height);

  write_field(&out, "ok", "1");
  write_field(&out, "payload", generated.payload);
  write_field(&out, "describe", app::describe(generated));
  write_field(&out, "filename", app::suggested_filename(inputs, generated));
  write_number(&out, "version", stats.version);
  write_number(&out, "modules", stats.size);
  write_number(&out, "mask", stats.mask);
  write_number(&out, "used_bytes", stats.used_bytes);
  write_number(&out, "capacity_bytes", stats.capacity_bytes);
  write_number(&out, "ecc_codewords", stats.ecc_codewords);
  write_number(&out, "verify_blocks", generated.verify_stats.blocks);
  write_number(&out, "px_w", width);
  write_number(&out, "px_h", height);
  write_number(&out, "preview_w", preview_width);
  write_number(&out, "preview_h", preview_height);
  return out;
}

}  // namespace bridge
