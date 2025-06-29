# bashketball

Bashketball is a two-player card game, where players compete in 3x3 basketball controlling teams of fantasy creatures.

## Project Components 

### Card Editor (in progress)

This includes a simple interface for editing and managing card legality.

### Deck Editor (unimplemented)

Players can create decks.

### Game Engine (unimplemented)

This also has a game engine for playing the game. 

## Development

### Install deps

```shell
npm i # install NPM deps
```

### Run the game

Start a Clojure REPL

``` clojure
user=> (dev)
dev=> (go)
dev=> (reset) ;; reload namespaces and restart components
```

### Connect to ClojureScript REPL

``` clojure
user=> (dev)
dev=> (cljs-repl)
```

### Running migrations

#### REPL Usage

``` clojure
user=> (require 'dev.migrate)
user=> (dev.migrate/migrate)
user=> (dev.migrate/rollback)
```

#### CLI Usage

``` sh
clj -M:dev -m dev.migrate migrate
clj -M:dev -m dev.migrate rollback
```

