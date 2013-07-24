CI.inner.Build = class Build extends CI.inner.Obj
  observables: =>
    messages: []
    build_time_millis: null
    committer_name: null
    committer_email: null
    committer_date: null
    author_name: null
    author_email: null
    author_date: null
    body: null
    start_time: null
    stop_time: null
    queued_at: null
    steps: []
    status: null
    lifecycle: null
    outcome: null
    failed: null
    infrastructure_fail: null
    dont_build: null
    name: null
    branch: "unknown"
    previous: null
    retry_of: null
    subject: null
    parallel: null
    usage_queued_at: null
    usage_queue_why: null
    usage_queue_visible: false

  clean: () =>
    super

    VM.cleanObjs(@steps())
    @clean_usage_queue_why()

  constructor: (json) ->

    steps = json.steps or []

    super(json)

    CI.inner.VcsUrlMixin(@)

    @steps(new CI.inner.Step(s, @) for s in steps)

    @url = @komp =>
      @urlForBuildNum @build_num

    @important_style = @komp =>
      switch @status()
        when "failed"
          true
        when "timedout"
          true
        when "no_tests"
          true
        else
          false

    @warning_style = @komp =>
      switch @status()
        when "infrastructure_fail"
          true
        when "killed"
          true
        when "not_run"
          true
        else
          false

    @success_style = @komp =>
      switch @outcome()
        when "success"
          true
        else
          false

    @info_style = @komp =>
      switch @status()
        when "running"
          true
        else
          false

    @style =
      "label-important": @important_style
      "label-warning": @warning_style

      "label-success": @success_style
      "label-info": @info_style
      label: true
      build_status: true

    @canceled = @komp =>
      @status() == 'canceled'

    @queued = @komp =>
      @status() == 'queued'

    @finished = @komp =>
      @stop_time()? or @canceled()

    @status_icon_class =
      "icon-ok": @success_style
      "icon-remove": @komp => @important_style() || @warning_style() || @canceled()
      "icon-repeat": @komp => @info_style() || @queued()
      "icon-spin": @komp => @info_style() || @queued()

    @status_words = @komp => switch @status()
      when "infrastructure_fail"
        "circle bug"
      when "timedout"
        "timed out"
      when "no_tests"
        "no tests"
      when "not_run"
        "not run"
      when "not_running"
        "not running"
      else
        @status()

    @why_in_words = @komp =>
      switch @why
        when "github"
          "GitHub push by #{@user.login}"
        when "edit"
          "Edit of the project settings"
        when "first-build"
          "First build"
        when "retry"
          "Manual retry of build #{@retry_of()}"
        when "ssh"
          "Retry of build #{@retry_of()}, with SSH enabled"
        when "auto-retry"
          "Auto-retry of build #{@retry_of()}"
        when "trigger"
          if @user
            "#{@user} on CircleCI.com"
          else
            "CircleCI.com"
        else
          if @job_name?
            @job_name
          else
            "unknown"

    @can_cancel = @komp =>
      if @status() == "canceled"
        false
      else
        switch @lifecycle()
          when "not_running"
            true
          when "running"
            true
          when "queued"
            true
          when "scheduled"
            true
          else
            false

    @pretty_start_time = @komp =>
      if @start_time()
        window.updator()
        CI.time.as_time_since(@start_time())

    @previous_build = @komp =>
      @previous()? and @previous().build_num

    @duration = @komp () =>
      if @start_time() and @stop_time()
        CI.time.as_duration(moment(@stop_time()).diff(moment(@start_time())))
      else
        if @status() == "canceled"
          # build was canceled from the queue
          "canceled"
        else if @start_time()
          CI.time.as_duration(@updatingDuration(@start_time()))


    # don't try to show queue information if the build is pre-usage_queue
    @show_queued_p = @komp =>
      @usage_queued_at()?

    @in_usage_queue_p = @komp =>
      not @finished() and not @queued_at()?

    @in_run_queue_p = @komp =>
      not @finished() and @queued_at()? and not @start_time()?

    @run_queued_time = @komp =>
      if @start_time() and @queued_at()
        moment(@start_time()).diff(@queued_at())
      else if @queued_at() and @stop_time() # canceled before left queue
        moment(@stop_time()).diff(@queued_at())
      else if @queued_at()
        @updatingDuration(@queued_at())

    @usage_queued_time = @komp =>
      if @usage_queued_at() and @queued_at()
        moment(@queued_at()).diff(@usage_queued_at())
      else if @usage_queued_at() and @stop_time() # canceled before left queue
        moment(@stop_time()).diff(@usage_queued_at())
      else if @usage_queued_at()
        @updatingDuration(@usage_queued_at())

    @queued_time = @komp =>
      (@run_queued_time() || 0) + (@usage_queued_time() || 0)

    @queued_time_summary = @komp =>
      if @run_queued_time()
        "#{CI.time.as_duration(@usage_queued_time())} waiting + #{CI.time.as_duration(@run_queued_time())} in queue"
      else
        "#{CI.time.as_duration(@usage_queued_time())} waiting for builds to finish"

    @branch_in_words = @komp =>
      return "(unknown)" unless @branch()

      b = @branch()
      b = b.replace(/^remotes\/origin\//, "")
      b = CI.stringHelpers.trimMiddle(b, 23)
      "(#{b})"

    @github_url = @komp =>
      return unless @vcs_revision
      "#{@vcs_url()}/commit/#{@vcs_revision}"

    @branch_url = @komp =>
      return unless @branch
      "#{@project_path()}/tree/#{@branch()}"

    @github_revision = @komp =>
      return unless @vcs_revision
      @vcs_revision.substring 0, 7

    @author = @komp =>
      @author_name() or @author_email()

    @committer = @komp =>
      @committer_name() or @committer_email()

    @committer_mailto = @komp =>
      if @committer_email()
        "mailto:#{@committer_email}"

    @author_mailto = @komp =>
      if @committer_email()
        "mailto:#{@committer_email()}"

    @author_isnt_committer = @komp =>
      (@committer_email() isnt @author_email()) or (@committer_name() isnt @author_name())

    @tooltip_title = @komp =>
      @status_words() + ": " + @build_num


   # hack - how can an action know its type is different from the previous, when
   # it doesn't even have access to the build
  different_type: (action) =>
    last = null
    breakLoop = false
    for s in @steps()
      for a in s.actions()
        if a == action
          breakLoop = true # no nested breaks in CS
          break
        last = a
      if breakLoop
        break

    last? and not (last.type() == action.type())

  urlForBuildNum: (num) =>
    "#{@project_path()}/#{num}"

  invite_user: (data, event) =>
    $.ajax
      url: "/api/v1/account/invite"
      type: "POST"
      event: event
      data: JSON.stringify
        invitee: @user
        vcs_url: @vcs_url()
        build_num: @build_num
    event.stopPropagation()


  visit: () =>
    SammyApp.setLocation @url()

  isRunning: () =>
    @start_time() and not @stop_time()

  shouldSubscribe: () =>
    @isRunning() or @status() == "queued" or @status() == "scheduled"

  maybeSubscribe: () =>
    if @shouldSubscribe()
      @build_channel = VM.pusher.subscribe(@pusherChannel())
      @build_channel.bind 'pusher:subscription_error', (status) ->
        _rollbar.push status

      @build_channel.bind('newAction', @newAction)
      @build_channel.bind('updateAction', @updateAction)
      @build_channel.bind('appendAction', @appendAction)
      @build_channel.bind('updateObservables', @updateObservables)
      @build_channel.bind('maybeAddMessages', @maybeAddMessages)

  fillActions: (step, index) =>
    # fills up steps and actions such that step and index are valid
    for i in [0..step]
      if not @steps()[i]?
        @steps.setIndex(i, new CI.inner.Step({}))

    # actions can arrive out of order when doing parallel. Fill up the other indices so knockout doesn't bitch
    for i in [0..index]
      if not @steps()[step].actions()[i]?
        @steps()[step].actions.setIndex(i, new CI.inner.ActionLog({}, @))

  newAction: (json) =>
    @fillActions(json.step, json.index)
    if old = @steps()[json.step].actions()[json.index]
      old.clean()
    @steps()[json.step].actions.setIndex(json.index, new CI.inner.ActionLog(json.log, @))

  updateAction: (json) =>
    # updates the observables on the action, such as end time and status.
    @fillActions(json.step, json.index)
    @steps()[json.step].actions()[json.index].updateObservables(json.log)

  appendAction: (json) =>
    # adds output to the action
    @fillActions(json.step, json.index)

    # @steps()[json.step].actions()[json.index].out.push(json.out)
    out = @steps()[json.step].actions()[json.index].out
    len = out().length
    last = out()[len - 1]
    payload = json.out
    if last? and last.type == payload.type
      out.valueWillMutate()
      last.message += payload.message
      out.valueHasMutated()
    else
      out.push(payload)

  maybeAddMessages: (json) =>
    existing = (message.message for message in @messages())
    (@messages.push(msg) if msg.message not in existing) for msg in json

  trackRetryBuild: (build, clearCache, SSH) =>
    mixpanel.track("Trigger Build",
      "vcs-url": build.project_name()
      "build-num": build.build_num
      "retry?": true
      "clear-cache?": clearCache
      "ssh?": SSH)

  # TODO: CSRF protection
  retry_build: (data, event, clearCache) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/retry"
      type: "POST"
      event: event
      success: (data) =>
        console.log("retry build data", data)
        console.log("retry event", event)
        build = new CI.inner.Build(data)
        build.clean() # no need to keep updating observables
        build.visit()
        @trackRetryBuild build, clearCache, false
    false

  clear_cache_and_retry_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/build-cache"
      type: "DELETE"
      event: event
      success: (data) =>
        @retry_build data, event, true
    false

  ssh_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/ssh"
      type: "POST"
      event: event
      success: (data) =>
        build = new CI.inner.Build(data)
        build.clean() # don't update observables
        build.visit()
        @trackRetryBuild build, false, true
    false

  cancel_build: (data, event) =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/cancel"
      type: "POST"
      event: event
    false

  toggle_usage_queue_why: () =>
    if @usage_queue_visible()
      @usage_queue_visible(!@usage_queue_visible())
      @clean_usage_queue_why()
      @usage_queue_why(null)
    else
      @load_usage_queue_why()
      @usage_queue_visible(true)

  clean_usage_queue_why: () =>
    if @usage_queue_why()
      VM.cleanObjs(@usage_queue_why())

  load_usage_queue_why: () =>
    $.ajax
      url: "/api/v1/project/#{@project_name()}/#{@build_num}/usage-queue"
      type: "GET"
      success: (data) =>
        @clean_usage_queue_why()
        @usage_queue_why(new CI.inner.Build(build_data) for build_data in data)
      complete: () =>
        # stop the spinner if there was an error
        @usage_queue_why([]) if not @usage_queue_why()

  report_build: () =>
    VM.raiseIntercomDialog('I think I found a bug in Circle at ' + window.location + '\n\n')

  description: (include_project) =>
    return unless @build_num?

    if include_project
      "#{@project_name()} ##{@build_num}"
    else
      @build_num

  pusherChannel: () =>
    "private-#{@project_name()}@#{@build_num}".replace(/\//g,"@")

  update: (json) =>
    @status(json.status)
