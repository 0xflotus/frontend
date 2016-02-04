# CircleCI's frontend

[![Circle CI](https://circleci.com/gh/circleci/frontend.svg?style=svg)](https://circleci.com/gh/circleci/frontend)

This is an open-source mirror of the code that is running
[CircleCI's](https://circleci.com) frontend. CircleCI provides powerful
Continuous Integration and Deployment with easy setup and maintenance.

Feel free to fork and make contributions. We'll try to get them into the main
application.

Watch [@brandonbloom](https://github.com/brandonbloom)'s Clojure/West talk, [Building CircleCI's Frontend with Om](https://www.youtube.com/watch?v=LNtQPSUi1iQ), for a quick overview.


Want to work with Clojure(Script) full-time? [We're hiring](https://circleci.com/jobs).


## Dependencies and Setup

### Node.js

Install [Node.js](http://nodejs.org/) and node dependencies:

```
npm install
```

Download all of the 3rd-party javascript dependencies:

```
node_modules/.bin/bower install
```

### Clojure

Install [Leiningen](http://leiningen.org/).

**Note:** You can also install leiningen via homebrew with `brew install leiningen`

### nginx

You'll need to install nginx to serve the site over `https` - we
require local development to user SSL to support using development
backends against production APIs in a secure way.

```
# first, install nginx
brew update
brew install nginx

# next, create a self-signed certificate
sudo mkdir /usr/local/etc/nginx/ssl && cd /usr/local/etc/nginx/ssl
sudo openssl req -batch -new \
      -x509 -newkey rsa:2048 -sha256 -nodes -days 365 \
      -subj '/C=US/ST=California/L=San Francisco/O=CircleCI/CN=*.circlehost' \
      -keyout /usr/local/etc/nginx/ssl/star.circlehost.key \
      -out /usr/local/etc/nginx/ssl/star.circlehost.crt
```

### Hosts

In your `/etc/hosts`, add the following line:

```
127.0.0.1 prod.circlehost
```

If you have access to the backend code, you can also add this line:

```
127.0.0.1 dev.circlehost
```
**Note:** Whichever of these you use _must_ be in your `/etc/hosts` to get
`lein figwheel` to run properly (steps in the next section)

## Usage

### Development Processes

You must use foreman (or goreman) to run the frontend and nginx.

```
foreman start # (or) goreman start
```

Then visit https://prod.circlehost:4443 or https://dev.circlehost:4443 (if you
have access and have set it up) in your browser.

### Viewing documentation changes

To see documentation locally you have to compile the docs manifest, like:

```
lein run -m frontend.tasks.http/precompile-assets
```

If you add a new document, you will have to re-run that; but if you just change
one of the existing documents it should show up with just a page refresh.

### Running the Karma Tests

You can run the tests locally with
````
node_modules/karma/bin/karma start karma.dev.conf.js --single-run
````

If you have karma-cli installed globally, you can say
````
karma start karma.dev.conf.js --single-run
````
instead.

Alternatively, you can leave a karma process running (`karma start karma.dev.conf.js`) and connect to it
and run the tests with `karma run`.

### Adding Tests

Take a look at `test-cljs/frontend/sample_test.cljs` for a starting point. Save a copy to the appropriate path for the namespace you want to test.

Karma won't automatically require the test namespaces, so open `test-js/require-karma.js` and add a require statement. Now the ns tests should run with every karma invocation.

### Asset Compilation Errors

If you are experiencing errors when building assets the following commands may
help reset all cached state:

```
lein clean
./node_modules/bower/bin/bower install
lein cljsbuild once
```

### Sanity Check

To test that everything worked, visit
https://prod.circlehost:4443/assets/css/app.css and
https://prod.circlehost:4443/assets/js/om-dev.js.stefon in your browser.

### Production & Development Backends

Now you should have everything you need to start hacking on Circle's frontend!

Visit https://prod.circlehost:4443 for the a production backend
with locally build development assets. Again, if you've got access to the
backend code (NOTE: it's not open source), you can run it locally on
`circlehost:8080`. To connect to the development backend, visit
https://dev.circlehost:4443. The dev server will default to dev assets, so you
don't need the query parameter.

### Browser REPL

Figwheel runs its own bREPL, which you'll see on the terminal at your `lein figwheel dev`. You can also run an additional bREPL over nREPL to connect Cider (or similar) to it. First, connect to the nREPL at localhost:7888. (This port is defined in project.clj.) Then, at the Cider REPL:

```clojure
(figwheel-sidecar.repl/cljs-repl "dev")
```

Unfortunately for vim-fireplace, Figwheel doesn't expose the repl-env to pass to `:Piggieback` in any nice way. (If you'd like to work on making it possible, have a look at `figwheel-sidecar.repl/repl-env`. Unfortunately, it takes an entire build map as an argument, rather than just a build ID, as `figwheel-sidecar.repl/cljs-repl` does.) However, you can still run an out-of-browser Rhino REPL using `:Piggieback` (which vim-fireplace will probably run for you automatically.)

### CLJS Dev Tools

[Dirac](https://github.com/binaryage/dirac) is a fork of Chrome DevTools that works for ClojureScript.

Requirements:

1. Mac OSX
2. [Google Chrome Canary](https://www.google.com/chrome/browser/canary.html) installed in /Applications
3. [Dirac Chrome Extension](https://chrome.google.com/webstore/detail/dirac-devtools/kbkdngfljkchidcjpnfcgcokkbhlkogi) installed in Chrome Canary

Running devtools:

1. Run the frontend as usual with `foreman`
2. In another terminal, `cd` to the project directory and run `./script/devtools.sh`. This will open a Chrome Canary window capable of running dev tools.
3. After the script opens Chrome Canary, click on the Dirac extension icon to the right of the address bar to open the dev tools.
4. Toggle CLJS on/off by pressing pgUp/pgDn (fn + up/down arrow on Mac) with focus on the prompt field.