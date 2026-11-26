---
title: Introduction
questions:
  - id: runtime
    prompt: Where does an **offline** app run?
    explanation: Everything is bundled into the `APK`.
    options:
      - text: On a remote server
      - text: Locally on your device
        correct: true
  - id: properties
    prompt: Which statements are true?
    options:
      - id: offline
        text: It works without internet access
        correct: true
      - id: account
        text: It requires an account
      - id: open
        text: It is open source
        correct: true
  - prompt: Pick the language used here.
    type: multiple
    options:
      - text: Kotlin
        correct: true
      - text: "Yes"
---
# Welcome

This is *emphasis*, **strong**, ~~gone~~ and `inline code` with a [link](https://example.org).

> A quoted line.

- First item
- Second item

1. One
2. Two

```kotlin
fun greet(name: String): String = "Hello, $name" // comment
```

| Name | Value |
|:-----|------:|
| alpha | 1 |
| beta | 2 |

![Avatar](../../../images/authors/sample-author.png)

---

Searchable keyword: zebrafish.
