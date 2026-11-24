# Documentation

These pages are shown on the website (`site/`) and read fine here on GitHub.

## How it is organised

```
docs/
  01-basics/
    01-getting-started.md
    02-using-the-editor.md
  02-editing/
    01-key-rows.md
    ...
```

- **A folder is a section** in the website's menu. Its name gives the section title (`02-editing` becomes "Editing"), and the number sets the order of sections.
- **A file is a page.** The first `# Heading` is its title, and the number in its name sets the order within the section.
- **The page address comes from the file name without its number**: `01-key-rows.md` is `/docs/key-rows` on the website. Names must be unique across all sections, so a page can move between sections without breaking its link.
- The first paragraph under the title is used as the page's summary on the documentation index, so start each page with a plain sentence.
- Link to another page with its file path, for example `[The key rows](../02-editing/01-key-rows.md)`, or `...01-key-rows.md#changing-the-keys` for a heading. Headings get an address from their text (`## Changing the keys` is `#changing-the-keys`).
- Only `##` and `###` headings appear in the "On this page" list.
- Adding a page or a section needs no code change: add the file and the menu, index, search and previous/next links pick it up.
