# Languages and highlighting

Highlighting is chosen from the file extension. It colors keywords, numbers, strings and comments using simple pattern matching, not a full parser, so unusual syntax can be colored imperfectly. It never changes your text.

| Shown in status bar | File extensions |
| --- | --- |
| C-like | `kt` `kts` `java` `js` `jsx` `ts` `tsx` `c` `h` `cpp` `hpp` `cc` `cs` `go` `rs` `swift` `dart` `php` `scala` |
| Python | `py` |
| Shell/Config | `sh` `bash` `zsh` `rb` `yml` `yaml` `toml` `conf` `ini` |
| Markup | `html` `htm` `xml` `svg` |
| JSON | `json` |
| Text | anything else |

Languages in the same row share one set of rules. A Kotlin file and a Rust file are both shown as "C-like" and use the same keyword list.

## Colors

| Color | Used for |
| --- | --- |
| Blue | Keywords and markup tags |
| Orange | Strings |
| Green | Comments |
| Light green | Numbers |

## Limits

- Highlighting updates about 0.2 seconds after you stop typing.
- Files longer than 150,000 characters are shown without highlighting, to keep typing smooth.
- Files with the extension `txt`, `md` or no extension are plain text.
