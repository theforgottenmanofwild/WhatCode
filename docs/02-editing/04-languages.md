# Languages and highlighting

The editor colors more than 30 languages and file types. The language is chosen from the file name, and the status bar shows its name, such as **JavaScript** or **YAML**. Highlighting reads the text with patterns, not a full parser, so unusual syntax can be colored imperfectly. It never changes your text.

## Languages

| Shown in status bar | File names |
| --- | --- |
| JavaScript | `js` `jsx` `mjs` `cjs` |
| TypeScript | `ts` `tsx` `mts` `cts` |
| Kotlin | `kt` `kts` |
| Java | `java` |
| Go | `go` |
| Go module | `go.mod` `go.work` |
| Rust | `rs` |
| C | `c` `h` |
| C++ | `cpp` `cc` `cxx` `hpp` `hh` `hxx` |
| C# | `cs` |
| Swift | `swift` |
| Dart | `dart` |
| PHP | `php` |
| Scala | `scala` `sc` |
| Python | `py` `pyw` |
| Ruby | `rb` `rake` `gemspec` |
| Shell | `sh` `bash` `zsh` `ksh` |
| Makefile | `Makefile` `GNUmakefile` `mk` |
| Dockerfile | `Dockerfile` |
| Gradle | `gradle` |
| SQL | `sql` |
| JSON | `json` |
| JSON with comments | `jsonc` |
| HTML | `html` `htm` `xhtml` |
| XML | `xml` `xsd` `xsl` `plist` |
| SVG | `svg` |
| CSS | `css` |
| SCSS | `scss` |
| Less | `less` |
| YAML | `yml` `yaml` |
| TOML | `toml` |
| INI | `ini` `cfg` `conf` `editorconfig` |
| Properties | `properties` |
| Markdown | `md` `markdown` |
| Text | anything else, such as `txt` or no extension |

Each language has its own words and rules. A Kotlin file is read as Kotlin and a Rust file as Rust, so `fun` and `fn` are keywords only where they should be.

## Colors

| Color | Used for |
| --- | --- |
| Blue | Keywords such as `const`, `fun` and `class`, `true`, `false` and `null`, and HTML tags |
| Purple | Words that steer the program (`if`, `for`, `return`), preprocessor lines such as `#include`, and CSS rules such as `@media` |
| Teal | Types and class names, such as `String` and `Foo` |
| Yellow | Functions and calls, annotations such as `@Override`, and Rust macros |
| Bright blue | Constants written in capitals, Ruby symbols, HTML entities and Markdown links |
| Light blue | Keys and properties: JSON, YAML and object keys, HTML attributes, CSS properties and shell variables |
| Orange | Strings |
| Light green | Numbers and CSS colors such as `#fff` |
| Green | Comments |
| Red | Regular expressions in JavaScript and TypeScript |
| Gold | CSS selectors |

## Mixed files

- An HTML file colors what is inside `<script>` as JavaScript and what is inside `<style>` as CSS.
- Strings and comments can run over several lines in the languages that allow it, such as `` `template strings` `` and `/* block comments */`.
- A string that is not closed is colored to the end of its line, so a missing quote does not color the rest of the file.
- Markdown colors headings, lists, quotes, links, bold text, inline code and fenced code blocks.

## Limits

- Highlighting updates about 0.2 seconds after you stop typing.
- A file longer than 20,000 characters is colored only where you see it: the lines on the screen and some above and below. Scrolling colors the lines that come into view a moment later.
- Long files are read in the background, so typing stays smooth. After a big edit the colors catch up a moment later.
- A line longer than 20,000 characters is not colored at all, so a minified file stays quick. See [Large files](../03-the-app/06-large-files.md).
- Files longer than 8,000,000 characters are shown without highlighting.
- Files with the extension `txt`, or with no extension, are plain text.
