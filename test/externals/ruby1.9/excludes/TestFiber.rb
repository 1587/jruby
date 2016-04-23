windows = RbConfig::CONFIG['host_os'] =~ /mswin|mingw/

exclude :test_alive, "needs investigation" if windows
exclude :test_fiber_transfer_segv, "wonky subprocess launching in test"
exclude :test_gc_root_fiber, "wonky subprocess launching in test"
exclude :test_many_fibers, "spins up too many fibers at once (10000)"
exclude :test_many_fibers_with_threads, "takes a very long time to run"
exclude :test_no_valid_cfp, "expects error where we and 2.0.0 do not present one"
exclude :test_resume_root_fiber, "why can main thread fiber resume itself but non-main thread cannot?"
