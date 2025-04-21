# blood-basket

## Development
```shell
npm i # install NPM deps
npm run dev # run dev build in watch mode with CLJS REPL
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

## Production
```shell
npm run release # build production bundle
```
