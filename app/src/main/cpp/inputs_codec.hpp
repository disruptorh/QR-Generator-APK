#ifndef QR_BRIDGE_INPUTS_CODEC_HPP_
#define QR_BRIDGE_INPUTS_CODEC_HPP_

// Wire format between the Kotlin editor and the C++ core.
//
// The core takes a plain app::AppInputs struct. The editor holds the same fields
// in Kotlin, so the two sides need one agreed-on text form. The format is a list
// of "key=value" lines: unknown keys are ignored, missing keys keep the core
// default, and every value is percent-escaped so a value can hold newlines, '='
// and UTF-8 without breaking the line framing.
//
// Percent-escaping covers every byte outside [A-Za-z0-9._~-], which keeps the
// framing trivial and makes the escape/unescape pair symmetric between the two
// languages.

#include <string>

#include "app/app_state.hpp"

namespace bridge {

// True for the characters that survive escaping unescaped.
bool is_plain(unsigned char c);

// Percent-escapes `value`. Used for input values and for the fields of the
// result record, which may also contain user text.
std::string escape(const std::string& value);

// Reverses escape(). A '%' that is not followed by two hex digits is kept
// verbatim, so a malformed line degrades to literal text instead of losing data.
std::string unescape(const std::string& value);

// Reads the wire form into `out`. Every recognised key is type-checked; a bad
// enum name, a bad number or a bad colour makes this return false and fill
// `error` with a message naming the key, rather than silently defaulting.
bool parse_inputs(const std::string& text, app::AppInputs* out, std::string* error);

// Serializes the outcome of app::generate(), using the same "key=value" lines.
// Always returns ok=1 or ok=0; a failed generation carries err.field and
// err.msg so the UI can point at the offending widget. On success it also
// carries the export size and the preview size, both computed by the core's own
// raster_size(), so no caller has to repeat that arithmetic.
std::string build_record(const app::AppInputs& inputs,
                         const core::Result<app::Generated>& result);

}  // namespace bridge

#endif  // QR_BRIDGE_INPUTS_CODEC_HPP_
