# EVC Compiler

**EVC** (Extended VC) is a multi-pass compiler, written in Java, for a statically typed C-like language. It extends the classic *VC* teaching language with a much richer C feature set (pointers, structs, multi-dimensional arrays, bitwise operators, mixed declarations and statements) plus selected C++-style features such as `new` and `nullptr`. EVC source programs are compiled to **Jasmin assembly**, which can be assembled into JVM bytecode and run on any standard Java Virtual Machine.

The project implements the full front-end and back-end pipeline by hand, without parser generators:

```
 EVC source ──► Scanner ──► Parser ──► AST ──► Checker ──► Code Generator ──► Jasmin (.j) ──► JVM bytecode
                (lexing)   (syntax)           (semantics)   (Jasmin output)
```

## Highlights

- **Hand-written recursive-descent compiler** with a clean separation between scanning, parsing, semantic analysis and code generation.
- **Rich type system**: `int`, `float`, `boolean`, `char`, `void`, user-defined `struct` types, pointers to any type, and multi-dimensional arrays.
- **Near-complete C expression language**: arithmetic, relational, logical, bitwise and shift operators, compound assignment (`+=`, `-=`, `*=`, `/=`, `%=`), pre/post increment and decrement, address-of and dereference, and field access via `.` and `->`.
- **Modern conveniences**: declarations may be mixed freely with statements inside any block, `for` loops can declare their loop variable, and `new` / `nullptr` are supported for dynamic allocation.
- **Aggregate initialisers**, including nested braces (`int m[2][3] = {{1,2,3},{4,5,6}};`) and string initialisers for `char` arrays.
- **Array parameter decay**: `char s[]` in a parameter list is treated as `char*`, as in C.
- **Centralised error reporting** shared by every compiler phase, with the compiler stopping before semantic analysis if syntax errors were found.

## Compiler Pipeline

| Phase | Component | Responsibility |
|-------|-----------|----------------|
| 1 | `Scanner` | Reads the source file and produces tokens: identifiers, integer / float / boolean / string / char literals, keywords, operators and delimiters. |
| 2 | `Parser` | Recursive-descent parser that checks the program against the EVC grammar and builds an abstract syntax tree (`AST`). |
| 3 | `Checker` | Semantic analysis over the AST: scoping, declaration checking and type checking. |
| 4 | Code generator | Traverses the checked AST and emits Jasmin assembly for the JVM. |

All phases report problems through a shared `ErrorReporter`. If the first pass (lexical and syntactic analysis) reports any errors, compilation stops with `Compilation was unsuccessful.`

## The EVC Language

### Types and declarations

```c
int x;
int *p;
int a[10];
struct Node *child[26];
float f(int x, char s[]);
```

- Base types: `int`, `float`, `boolean`, `char`, `void`, and `struct IDENT`.
- Declarators support any number of pointer prefixes and array suffixes.
- Global variables, functions (declaration or definition) and `struct` definitions may appear in any order at the top level.

### Statements

`if` / `else`, `while`, `for`, `return`, `break`, `continue`, expression statements, empty statements and nested compound statements. Local variable declarations (with optional initialisers) may appear anywhere inside a block, and also as the first clause of a `for` loop.

### Expressions

Operators, from lowest to highest precedence:

| Level | Operators |
|-------|-----------|
| Assignment | `=` `+=` `-=` `*=` `/=` `%=` |
| Logical OR / AND | `\|\|` &nbsp; `&&` |
| Bitwise OR / XOR / AND | `\|` &nbsp; `^` &nbsp; `&` |
| Equality | `==` `!=` |
| Relational | `<` `<=` `>` `>=` |
| Shift | `<<` `>>` |
| Additive | `+` `-` |
| Multiplicative | `*` `/` `%` |
| Unary | `+` `-` `!` `~` `&` `*` `++` `--` |
| Postfix | `[ ]` `( )` `.` `->` `++` `--` |

Primary expressions are identifiers, literals (int, float, boolean, char, string), `nullptr`, `new IDENT` and parenthesised expressions.

The complete grammar is documented in [`EVC_grammar.md`](EVC_grammar.md).

### Example

```c
struct Node {
    int value;
    struct Node *next;
};

int sum(int a[], int n) {
    int total = 0;
    for (int i = 0; i < n; i++) {
        total += a[i];
    }
    return total;
}

int main() {
    int data[4] = {1, 2, 3, 4};
    struct Node *head = nullptr;
    int result = sum(data, 4);
    return 0;
}
```

## Project Structure

```
EVC/
├── evc.java          # Compiler driver (entry point)
├── ErrorReporter     # Shared error collection and reporting
├── Scanner/          # Lexical analysis (Scanner, SourceFile, tokens)
├── Parser/           # Recursive-descent parser
├── ASTs/             # Abstract syntax tree node classes
├── Checker/          # Semantic analysis
└── ...               # Code generation (Jasmin emitter)
```

> This repository snapshot may include only the driver and documentation; AST and other supporting sources are omitted where noted.

## Requirements

- Java Development Kit (JDK) 8 or later
- [Jasmin](https://github.com/davidar/jasmin) assembler, to turn the generated `.j` files into `.class` files

## Build and Run

**1. Compile the compiler** (from the project root):

```bash
javac EVC/*.java EVC/*/*.java
```

**2. Compile an EVC program:**

```bash
java EVC.evc test.evc
```

The driver prints its progress:

```
======= The EVC Compiler =======

Pass 1: Lexical and Syntactic Analysis
Pass 2: Semantic Analysis
Compilation was successful.
```

**3. Assemble and run the generated Jasmin code:**

```bash
java -jar jasmin.jar test.j
java test
```

If any phase reports an error, the compiler prints `Compilation was unsuccessful.` and stops.

## Design Notes

- **Staged error handling**: syntax errors halt compilation before semantic analysis, so the checker only ever sees a well-formed AST.
- **Grammar-driven parser**: each non-terminal in `EVC_grammar.md` corresponds to a parsing routine, which keeps the implementation easy to extend (for example, adding new operators or statements).
- **JVM as the target**: generating Jasmin rather than native code makes the output portable and allows direct comparison with `javac`-style bytecode.

## Future Work

- Optimisation passes over the AST or generated bytecode
- Additional C++-style features
- A regression suite of EVC test programs with expected outputs
