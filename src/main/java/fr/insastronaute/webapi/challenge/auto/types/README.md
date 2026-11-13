# Automatic challenge type extension guide

Each child package is an autonomous Spring plugin for one stable automatic
challenge type. The engine discovers `AutoChallengeType<?>` beans; central
registry, evaluation, scheduling, and award code must never import a concrete
type.

## Package contract

Create one lowercase package named after the stable type, for example:

```text
types/eventattendance/
├── EventAttendanceChallengeType.java
├── EventAttendanceParameters.java
└── README.md
```

The package may contain a dedicated repository or query service when its
candidate selection is complex. Prefer an existing domain repository when the
query belongs naturally to that domain. A type must not create submissions,
award points, or call another automatic challenge type.

## Implementation checklist

1. Choose an immutable, uppercase stable key. Persisted challenges depend on it,
   so never rename or reuse it.
2. Implement `AutoChallengeType<P>` and register the implementation as a Spring
   bean with `@Component`.
3. Define a typed parameter class. Reject unknown or malformed JSON. Use an
   explicit empty parameter type when the plugin has no configuration.
4. Describe every configurable field through `parameters()` so admin clients can
   render forms without knowing the Java implementation.
5. Enforce every server-side constraint in the parameter constructor/creator or
   through `validate(P)`. Parameter metadata only describes the admin form and
   is not enforced by the registry. Types without additional constraints may
   keep the default `validate(P)` implementation.
6. Select candidates with a bounded query, deterministic ordering, and a strictly
   advancing UUID cursor. Return `nextCursor = null` for the final page.
7. Add database indexes for the candidate predicate and ordering. Document them
   in the plugin README and add schema changes through Flyway.
8. Test the stable metadata, parameter parsing, business validation, first and
   final pages, cursor behavior, and candidate predicate.
9. Document configuration examples, retroactivity, repository/index usage, and
   domain assumptions in the package README.

## Minimal skeleton

```java
@Component
public final class ExampleChallengeType implements AutoChallengeType<ExampleParameters> {
    public String key() { return "EXAMPLE"; }
    public String publicName() { return "Example"; }
    public String description() { return "Example automatic condition."; }
    public Class<ExampleParameters> parametersType() { return ExampleParameters.class; }
    public List<AutoChallengeParameter> parameters() { return List.of(); }

    public AutoChallengeCandidatePage findCandidates(
            ExampleParameters parameters, UUID afterUserId, int limit) {
        // Query at most limit + 1 rows, expose at most limit IDs, and derive the cursor.
    }
}
```

Common eligibility, duplicate protection, transactions, and recalculation remain
the responsibility of the engine.

## Dependency direction

Allowed dependencies flow inward as follows:

```text
scheduling ──> evaluation ──> award
     │              │           │
     └──────── configuration ────┘
                    │
registry ─────────> api <──────── types/*
```

`api` contains contracts only. `configuration` is neutral shared configuration.
The registry knows only API contracts. Evaluation may orchestrate the registry
and award service. Scheduling triggers evaluation. Concrete types may use their
own domain repositories but no engine package may import a concrete type.
