import ratpack.codahale.metrics.CodaHaleMetricsModule
import ratpack.file.internal.DefaultFileSystemBinding
import ratpack.groovy.templating.TemplatingModule
import ratpack.jackson.JacksonModule
import ratpack.remote.RemoteControlModule
import ratpack.rx.RxRatpack
import ratpack.site.SiteModule
import ratpack.site.github.GitHubApi
import ratpack.site.github.GitHubData
import ratpack.site.github.RatpackVersions

import static ratpack.groovy.Groovy.groovyTemplate
import static ratpack.groovy.Groovy.ratpack
import static ratpack.registry.Registries.just

ratpack {
  bindings {
    add \
      new JacksonModule(),
      new CodaHaleMetricsModule().metrics(),
      new SiteModule(launchConfig),
      new RemoteControlModule()

    config(TemplatingModule).staticallyCompile = true

    RxRatpack.initialize()
  }

  handlers {

    def longCache = 60 * 60 * 24 * 365
    def shortCache = 60 * 10 // ten mins

    handler {
      if (request.headers.get("host").endsWith("ratpack-framework.org")) {
        redirect 301, "http://www.ratpack.io"
        return
      }

      if (request.path.empty || request.path == "index.html") {
        response.headers.set "X-UA-Compatible", "IE=edge,chrome=1"
      }

      next()
    }

    prefix("assets") {
      handler {
        response.headers.add("Cache-Control", "max-age=$shortCache, public")
        next()
      }
      assets "assets"
    }

    // The generated CSS has links to /images, remap
    // https://github.com/robfletcher/gradle-compass/issues/12
    prefix("images") {
      handler {
        response.headers.add("Cache-Control", "max-age=$shortCache, public")
        next()
      }
      assets "assets/images"
    }

    get("index.html") {
      redirect 301, "/"
    }

    get {
      render groovyTemplate("index.html")
    }

    handler("reset") { GitHubApi gitHubApi ->
      byMethod {
        if (launchConfig.reloadable) {
          get {
            gitHubApi.invalidateCache()
            render "ok"
          }
        }
        post {
          gitHubApi.invalidateCache()
          render "ok"
        }
      }
    }

    prefix("versions") {
      get { RatpackVersions versions ->
        versions.all.subscribe { RatpackVersions.All all ->
          render groovyTemplate("versions.html", versions: all)
        }
      }

      prefix(":version") {
        get { RatpackVersions versions, GitHubData gitHubData ->
          versions.all.subscribe { RatpackVersions.All all ->
            def version = all.find(allPathTokens.version)
            if (version == null) {
              clientError(404)
            } else {
              gitHubData.closed(version).subscribe {
                render groovyTemplate("version.html", version: version, issues: it)
              }
            }
          }
        }
      }
    }

    prefix("manual") {
      fileSystem("manual") {
        get {
          redirect 301, "manual/current/"
        }

        prefix(":label") {
          handler { RatpackVersions versions ->
            def label = pathTokens.label

            versions.all.subscribe { RatpackVersions.All allVersions ->
              if (label == "current" || label in allVersions.released.version) {
                response.headers.add("Cache-Control", "max-age=$longCache, public")
              } else if (label == "snapshot" || label in allVersions.unreleased.version) {
                response.headers.add("Cache-Control", "max-age=$shortCache, public")
              }

              def version
              if (label == "current") {
                version = allVersions.current
              } else if (label == "snapshot") {
                version = allVersions.snapshot
              } else {
                version = allVersions.find(label)
                if (version == null) {
                  clientError 404
                  return
                }
              }

              next(just(new DefaultFileSystemBinding(file(version.version))))
            }
          }
          assets ""
        }
      }
    }

    assets "public"
  }
}
