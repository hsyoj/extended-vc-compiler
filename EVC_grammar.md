# The grammar for the EVC language

This document specifies the EVC grammar (informal EBNF) corresponding to the final compiler implementation.

## Lexical categories

Identifiers, integer literals, float literals, boolean literals (`true`, `false`), string literals, and character literals are supported.

Reserved words include:

- `boolean`, `break`, `char`, `continue`, `else`, `float`, `for`, `if`, `int`, `return`
- `struct`, `void`, `while`
- `nullptr`, `new`

Operators and punctuation include:

- Arithmetic: `+ - * / %`
- Increment/decrement: `++ --`
- Compound assignment: `+= -= *= /= %=`
- Relational/equality: `< <= > >= == !=`
- Logical: `! && ||`
- Bitwise: `& | ^ ~ << >>`
- Address / pointer / field access: `& * . ->`
- Assignment: `=`
- Delimiters: `{ } ( ) [ ] ; ,`

## Program structure

```ebnf
program          ::= decl-list EOF

decl-list        ::= { decl }

decl             ::= struct-def
                   | func-decl-or-def
                   | global-var-decl-list ';'

struct-def       ::= 'struct' IDENT '{' struct-field-list '}' ';'

struct-field-list ::= { base-type declarator-list ';' }

func-decl-or-def ::= base-type declarator '(' para-list ')' ';'
                   | base-type declarator '(' para-list ')' compound-stmt

global-var-decl-list ::= init-declarator { ',' init-declarator }
init-declarator  ::= declarator [ '=' initialiser ]
```

## Types and declarators

```ebnf
base-type        ::= 'int'
                   | 'float'
                   | 'boolean'
                   | 'char'
                   | 'void'
                   | 'struct' IDENT

declarator       ::= pointer-prefix IDENT array-suffixes
pointer-prefix   ::= { '*' }
array-suffixes   ::= { '[' [ INTLITERAL ] ']' }
```

Examples accepted by the parser:

```c
int x;
int *p;
int a[10];
struct Node *child[26];
float f(int x, char s[]);
```

## Parameters

```ebnf
para-list        ::= /* empty */
                   | 'void'
                   | para-decl { ',' para-decl }

para-decl        ::= base-type declarator
```

Parameter arrays decay to pointer types semantically, for example `char s[]` behaves like `char*` when passed to a function.

## Statements

```ebnf
compound-stmt    ::= '{' mixed-stmt-list '}'
mixed-stmt-list  ::= { local-var-decl-list ';' | stmt }

local-var-decl-list ::= init-declarator { ',' init-declarator }

stmt             ::= compound-stmt
                   | if-stmt
                   | while-stmt
                   | for-stmt
                   | return-stmt
                   | break ';'
                   | continue ';'
                   | expr ';'
                   | ';'

if-stmt          ::= 'if' '(' expr ')' stmt [ 'else' stmt ]
while-stmt       ::= 'while' '(' expr ')' stmt
for-stmt         ::= 'for' '(' for-init ';' [ expr ] ';' [ expr ] ')' stmt
for-init         ::= /* empty */
                   | expr
                   | local-var-decl-list

return-stmt      ::= 'return' [ expr ] ';'
```

The parser allows mixed declarations and statements inside blocks, so declarations do not have to appear only at the beginning of a block.

## Expressions

Expression precedence from lowest to highest:

```ebnf
expr             ::= assign-expr

assign-expr      ::= logical-or-expr
                   | logical-or-expr assign-op assign-expr
assign-op        ::= '=' | '+=' | '-=' | '*=' | '/=' | '%='

logical-or-expr  ::= logical-and-expr { '||' logical-and-expr }
logical-and-expr ::= bitwise-or-expr { '&&' bitwise-or-expr }
bitwise-or-expr  ::= bitwise-xor-expr { '|' bitwise-xor-expr }
bitwise-xor-expr ::= bitwise-and-expr { '^' bitwise-and-expr }
bitwise-and-expr ::= equality-expr { '&' equality-expr }
equality-expr    ::= relational-expr { ('==' | '!=') relational-expr }
relational-expr  ::= shift-expr { ('<' | '<=' | '>' | '>=') shift-expr }
shift-expr       ::= additive-expr { ('<<' | '>>') additive-expr }
additive-expr    ::= multiplicative-expr { ('+' | '-') multiplicative-expr }
multiplicative-expr ::= unary-expr { ('*' | '/' | '%') unary-expr }

unary-expr       ::= ('+' | '-' | '!' | '~' | '&' | '*' | '++' | '--') unary-expr
                   | postfix-expr

postfix-expr     ::= primary-expr
                      { '[' expr ']'
                      | '(' arg-list ')'
                      | '.' IDENT
                      | '->' IDENT
                      | '++'
                      | '--' }

primary-expr     ::= IDENT
                   | INTLITERAL
                   | FLOATLITERAL
                   | BOOLEANLITERAL
                   | CHARLITERAL
                   | STRINGLITERAL
                   | 'nullptr'
                   | 'new' IDENT
                   | '(' expr ')'
```

## Initialisers

```ebnf
initialiser      ::= expr
                   | '{' [ initialiser { ',' initialiser } ] '}'
```

This supports examples such as:

```c
int a[10] = {0};
int b[4] = {1, 1, 1, 1};
int m[2][3] = {{1,2,3},{4,5,6}};
char s[16] = "hello";
```
