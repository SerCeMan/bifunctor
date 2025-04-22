package dev.bifunctor.ide.ui.markdown

import dev.bifunctor.ide.ui.components.markdown.MarkdownText
import dev.bifunctor.ide.ui.story

private val markdownContent = """
# ✅ Task Completed

Here's a breakdown of what was done:

## 📝 Summary

- Implemented the `PersonTest` class
- Added two unit tests:
  - `shouldNotValidateWhenFirstNameEmpty`
  - `shouldNotValidateWhenLastNameEmpty`
- Verified tests fail when required fields are blank ✅

## 🧪 Code Example

```kotlin
@Test
fun shouldNotValidateWhenFirstNameEmpty() {
    val person = Person().apply {
        firstName = ""
        lastName = "Smith"
    }

    val violations = validator.validate(person)
    assertThat(violations).hasSize(1)
}
"""

fun main() = story {
  MarkdownText(markdownContent)
}
