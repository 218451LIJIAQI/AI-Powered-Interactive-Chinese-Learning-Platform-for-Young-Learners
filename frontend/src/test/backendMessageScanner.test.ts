import { describe, expect, it } from 'vitest'

import { BackendMessageExtractor, javaStringLiterals, messageText, tokenize } from './backendMessageScanner'

describe('backend message source scanning', () => {
  it('joins adjacent Java string literals and ignores quoted text inside comments', () => {
    const extractor = new BackendMessageExtractor([
      { path: 'Example.java', source: 'class Example { String s = "One " + /* "retired" */ "message."; }' },
    ])
    expect(javaStringLiterals(extractor.files)).toEqual(new Set(['one message']))
  })

  it.each([
    ['class Example { /* unfinished', 'Unterminated Java block comment.'],
    ['class Example { String message = """ unfinished', 'Unterminated Java text block.'],
    ['class Example { String message = "unfinished', 'Unterminated Java string literal.'],
    ['class Example { String message = "ends with escape\\', 'Unterminated Java string literal.'],
    ["class Example { char c = 'x", 'Unterminated Java character literal.'],
  ])('rejects an unfinished source instead of looping: %s', (source, message) => {
    expect(() => tokenize(source)).toThrow(message)
  })

  it('unescapes Java escapes including unicode sequences, quotes, and backslashes', () => {
    const tokens = tokenize('String s = "Line\\n\\t\\"quoted\\" \\\\ \\u4f60\\u597d";')
    const text = tokens.find((token) => token.kind === 'text')
    expect(text?.value).toBe('Line\n\t"quoted" \\ 你好')
  })

  it('reports a recursive variable expansion instead of overflowing the stack', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Example.java',
        source: `class Example {
          void fail() {
            String reason = "Invalid value";
            reason = reason + "!";
            throw new ResponseStatusException(BAD_REQUEST, reason);
          }
        }`,
      },
    ])
    expect(() => extractor.extract()).toThrow('Cyclic message expression in Example.java.')
    // A failed scan must not leave expansion state behind for the next attempt.
    expect(() => extractor.extract()).toThrow('Cyclic message expression in Example.java.')
  })

  it('reads a helper that picks one of several fixed messages', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Example.java',
        source: `class Example {
          void archive(Item item) {
            throw new ResponseStatusException(CONFLICT, refusal(item));
          }
          private static String refusal(Item item) {
            if (item.isNew()) {
              return "This item is new.";
            }
            return "This item is in use.";
          }
        }`,
      },
    ])
    expect(extractor.extract().map(({ status, parts }) => `${status} ${messageText(parts)}`)).toEqual([
      '409 This item is new.',
      '409 This item is in use.',
    ])
  })

  it('follows polymorphic problemWith validation across inner supports and keeps an unfamiliar return visible', () => {
    const messages = (last: string) =>
      new BackendMessageExtractor([
        {
          path: 'PracticeCatalog.java',
          source: `class PracticeCatalog {
          static final String REQUIRED = "A set needs material.";
          abstract class TypeSupport {
            abstract String problemWith(Request request);
            void validate(Request request) {
              String problem = problemWith(request);
              if (problem != null) throw new ResponseStatusException(BAD_REQUEST, problem);
            }
          }
          final class Cards extends TypeSupport {
            String problemWith(Request request) {
              if (request.isEmpty()) return REQUIRED;
              return null;
            }
          }
          final class Tracing extends TypeSupport {
            String problemWith(Request request) {
              if (request.missingStrokes()) return "Enter the strokes.";
              return ${last};
            }
          }
        }`,
        },
      ])
        .extract()
        .map(({ parts }) => messageText(parts))

    expect(messages('null')).toEqual(['A set needs material.', 'Enter the strokes.'])
    expect(messages('unknownSupplier.get()')).toEqual(['{}'])
  })

  it('reads each text a switch helper picks, and a sum in brackets as one value', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Example.java',
        source: `class Example {
          void archive(Item item, int rank) {
            throw new ResponseStatusException(CONFLICT, "Rank " + (rank + 1) + " keeps the " + kind(item) + ".");
          }
          private static String kind(Item item) {
            return switch (item.type()) {
              case NEW -> "new item";
              case USED, BROKEN -> "used item";
            };
          }
        }`,
      },
    ])
    expect(extractor.extract().map(({ status, parts }) => `${status} ${messageText(parts)}`)).toEqual([
      '409 Rank {} keeps the new item.',
      '409 Rank {} keeps the used item.',
    ])
  })

  it('tracks fixed and templated messages thrown through parameterized HTTP status helpers', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Example.java',
        source: `class Example {
          void handle(User user) {
            failFixed(CONFLICT);
            failDetail(BAD_REQUEST, user.getName());
            failDetail(NOT_FOUND, "Lesson");
          }
          private void failFixed(HttpStatus status) {
            throw new ResponseStatusException(status, "Fixed text.");
          }
          private void failDetail(HttpStatus status, String detail) {
            throw new ResponseStatusException(status, detail + " is invalid.");
          }
        }`,
      },
    ])
    expect(
      extractor
        .extract()
        .map(({ status, parts, example }) => `${status} ${example ? 'example' : 'template'}: ${messageText(parts)}`)
        .sort(),
    ).toEqual([
      '400 template: {} is invalid.',
      '404 example: Lesson is invalid.',
      '404 template: {} is invalid.',
      '409 template: Fixed text.',
    ])
  })

  it('follows a notification text through the methods that pass it on to where it is written', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'NotificationService.java',
        source: `class NotificationService {
          public void safeCreateNotification(User user, String title, String message) { create(user, title, message); }
          private void create(User user, String title, String message) {
            Notification notification = new Notification();
            notification.setTitle(title);
            notification.setMessage(message);
          }
        }`,
      },
      {
        path: 'LessonService.java',
        source: `class LessonService {
          private static final String TITLE = "Lesson moved";
          void move(Lesson lesson, boolean early) {
            tell(lesson.getTeacher(), TITLE, lesson.getTitle() + (early ? " starts earlier." : " starts later."));
            notificationService.safeCreateNotification(lesson.getTeacher(), "Note", summary(lesson));
            notificationService.safeCreateNotification(lesson.getTeacher(), String.format("%s", lesson), "x" + lesson);
          }
          private void tell(User user, String title, String message) {
            notificationService.safeCreateNotification(user, title, message);
          }
          static String summary(Lesson lesson) {
            return "About " + lesson.getTitle() + ".";
          }
        }`,
      },
    ])
    const texts = extractor
      .notificationTexts()
      .map(({ file, field, parts }) => `${file} ${field}: ${messageText(parts)}`)
    // String.format is not parsed: it remains a single `{}` hole, as asserted below.
    expect(texts.sort()).toEqual([
      'LessonService.java message: About {}.',
      'LessonService.java message: x{}',
      'LessonService.java message: {} starts earlier.',
      'LessonService.java message: {} starts later.',
      'LessonService.java title: Lesson moved',
      'LessonService.java title: Note',
      'LessonService.java title: {}',
    ])
  })

  it('reads stringList, cross-class constants, zero-arg fixed getters, and keeps local finals out of class constants', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Shared.java',
        source: `class Shared {
          private static final List<String> HINTS = List.of("first hint", "second hint");
          static final String SHARED_MSG = "From shared constant.";
          static String fixedGetter() { return "From fixed getter."; }
          void shadow() {
            final String SHARED_MSG = "Local shadow should not leak.";
            log(SHARED_MSG);
          }
        }`,
      },
      {
        path: 'Caller.java',
        source: `class Caller {
          void fail(boolean first) {
            throw new ResponseStatusException(BAD_REQUEST, first ? Shared.SHARED_MSG : Shared.fixedGetter());
          }
        }`,
      },
    ])
    expect(extractor.stringList('Shared', 'HINTS')).toEqual(['first hint', 'second hint'])
    expect(extractor.stringConstant('Shared', 'SHARED_MSG')).toBe('From shared constant.')
    expect(
      extractor
        .extract()
        .map(({ parts }) => messageText(parts))
        .sort(),
    ).toEqual(['From fixed getter.', 'From shared constant.'])
  })

  it('resolves record-component forwarding using the receiver declared type without leaking unrelated records', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'UploadPolicy.java',
        source: `public record UploadPolicy(long maxBytes, String tooLargeMessage) {}`,
      },
      {
        path: 'OtherDto.java',
        source: `public record OtherDto(String tooLargeMessage) {}`,
      },
      {
        path: 'UploadService.java',
        source: `class UploadService {
          static final UploadPolicy IMAGE = new UploadPolicy(1024, "Image is too large.");
          void check(UploadPolicy policy) {
            throw new ResponseStatusException(PAYLOAD_TOO_LARGE, policy.tooLargeMessage());
          }
          void unusedOther() {
            new OtherDto("Unrelated DTO message.");
          }
        }`,
      },
    ])
    expect(extractor.extract().map(({ status, parts }) => `${status} ${messageText(parts)}`)).toEqual([
      '413 Image is too large.',
    ])
  })

  it('handles nested ternaries, generic wildcards, parenthesised constants, and numeric sums cleanly', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Example.java',
        source: `class Example {
          static final String EARLY = "too early";
          static final String LATE = "too late";
          void fail(boolean a, boolean b, Object list, int count) {
            throw new ResponseStatusException(BAD_REQUEST, a ? b ? "First." : "Second." : "Third.");
            throw new ResponseStatusException(BAD_REQUEST, list instanceof List<?> ? "Empty list." : "Full list.");
            throw new ResponseStatusException(BAD_REQUEST, "Item " + (countOf("a") + 1) + " is " + (a ? EARLY : LATE) + ".");
          }
        }`,
      },
    ])
    expect(
      extractor
        .extract()
        .map(({ parts }) => messageText(parts))
        .sort(),
    ).toEqual([
      'Empty list.',
      'First.',
      'Full list.',
      'Item {} is too early.',
      'Item {} is too late.',
      'Second.',
      'Third.',
    ])
  })

  it('treats compound += local mutations as opaque and ignores assignments after the throw', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Example.java',
        source: `class Example {
          void compounded() {
            String msg = "Base";
            msg += " extra";
            throw new ResponseStatusException(BAD_REQUEST, msg);
          }
          void assignedLater(boolean stop) {
            String msg = "Before throw.";
            if (stop) throw new ResponseStatusException(BAD_REQUEST, msg);
            msg = "After throw.";
          }
        }`,
      },
    ])
    expect(
      extractor
        .extract()
        .map(({ parts }) => messageText(parts))
        .sort(),
    ).toEqual(['Before throw.', '{}'])
  })

  it('reads return statements inside arrow-switch arms while still skipping lambda block bodies', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Example.java',
        source: `class Example {
          void run(Item item) {
            throw new ResponseStatusException(BAD_REQUEST, reason(item));
          }
          private static String reason(Item item) {
            switch (item.kind()) {
              case A -> {
                item.tags().stream().filter(t -> {
                  return t.isActive();
                }).toList();
                return "Kind A is closed.";
              }
              default -> {
                return "Kind is closed.";
              }
            }
          }
        }`,
      },
    ])
    expect(
      extractor
        .extract()
        .map(({ parts }) => messageText(parts))
        .sort(),
    ).toEqual(['Kind A is closed.', 'Kind is closed.'])
  })

  it('tracks multi-hop status and multi-slot parameter forwarding while keeping private sinks and uncalled sinks accurate', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'A.java',
        source: `class A {
          void entry() {
            relay(NOT_FOUND, "Audio", "URL");
          }
          private void relay(HttpStatus status, String kind, String field) {
            fail(status, kind, field);
          }
          private void fail(HttpStatus status, String kind, String field) {
            throw new ResponseStatusException(status, kind + " " + field + " is invalid.");
          }
          void uncalled(String secret) {
            throw new ResponseStatusException(BAD_REQUEST, secret);
          }
        }`,
      },
      {
        path: 'B.java',
        source: `class B {
          void unrelated() {
            this.fail(CONFLICT, "Ignored", "Call");
          }
        }`,
      },
    ])
    expect(
      extractor
        .extract()
        .map(({ status, parts, example }) => `${status} ${example ? 'example' : 'template'}: ${messageText(parts)}`)
        .sort(),
    ).toEqual(['400 template: {}', '404 example: Audio URL is invalid.', '404 template: {} {} is invalid.'])
  })

  it('recognises var-declared Notifications, scopes entities per method, and keeps uncalled notification sinks visible', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'NoticeService.java',
        source: `class NoticeService {
          void send(String body) {
            var note = new Notification();
            note.setTitle("Alert");
            note.setMessage("Detail: " + body);
          }
          void other(OtherEntity note) {
            note.setTitle("Not a notification");
          }
        }`,
      },
    ])
    expect(
      extractor
        .notificationTexts()
        .map(({ field, parts }) => `${field}: ${messageText(parts)}`)
        .sort(),
    ).toEqual(['message: Detail: {}', 'title: Alert'])
  })

  it('indexes constants and methods inside Java records without treating the record header as a method', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'EmailSettings.java',
        source: `public record EmailSettings(String host) {
          static final String INVALID = "Host is invalid.";
          void validate() {
            throw new ResponseStatusException(BAD_REQUEST, INVALID);
          }
        }`,
      },
    ])
    expect(extractor.stringConstant('EmailSettings', 'INVALID')).toBe('Host is invalid.')
    expect(extractor.extract().map(({ parts }) => messageText(parts))).toEqual(['Host is invalid.'])
  })

  it('keeps punctuation string literals from confusing arrow-switch arms and ternary branches', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Punctuation.java',
        source: `class Punctuation {
          void check(String token, boolean comma) {
            throw new ResponseStatusException(BAD_REQUEST, explain(token));
            throw new ResponseStatusException(BAD_REQUEST, "Expected " + (comma ? "," : ";") + ".");
          }
          private static String explain(String token) {
            switch (token) {
              case ";" -> {
                return "Semicolon is not allowed.";
              }
              case "(" -> {
                return "Parenthesis is not allowed.";
              }
              default -> {
                return "Token is not allowed.";
              }
            }
          }
        }`,
      },
    ])
    expect(
      extractor
        .extract()
        .map(({ parts }) => messageText(parts))
        .sort(),
    ).toEqual([
      'Expected ,.',
      'Expected ;.',
      'Parenthesis is not allowed.',
      'Semicolon is not allowed.',
      'Token is not allowed.',
    ])
  })

  it('keeps parenthesised arithmetic with int/long constants opaque while concatenating string constants', () => {
    const extractor = new BackendMessageExtractor([
      {
        path: 'Limits.java',
        source: `class Limits {
          static final int MAX = 5;
          static final String PREFIX = "v";
          void fail(int count) {
            throw new ResponseStatusException(BAD_REQUEST, "Limit is " + (MAX + 1) + " and " + (count + MAX) + ".");
            throw new ResponseStatusException(BAD_REQUEST, "Tag " + (PREFIX + 1) + ".");
          }
        }`,
      },
    ])
    expect(
      extractor
        .extract()
        .map(({ parts }) => messageText(parts))
        .sort(),
    ).toEqual(['Limit is {} and {}.', 'Tag v1.'])
  })
})
