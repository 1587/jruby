windows = RbConfig::CONFIG['host_os'] =~ /mswin|mingw/

exclude :test_errat, "needs investigation"
exclude :test_exception_to_s_should_not_propagate_untrustedness, "needs investigation"
exclude :test_nomethoderror, "needs investigation"
exclude :test_safe4, "needs investigation" if windows
exclude :test_set_backtrace, "needs investigation"
exclude :test_thread_signal_location, "needs investigation"
exclude :test_to_s_taintness_propagation, "needs investigation"
exclude :test_catch_throw_in_require, "needs investigation"
