windows = RbConfig::CONFIG['host_os'] =~ /mswin|mingw/

exclude :test_ascii_incompatible_path, "needs investigation"
exclude :test_basename, "needs investigation"
exclude :test_dirname, "needs investigation"
exclude :test_extname, "needs investigation"
exclude :test_join, "needs investigation"
exclude :test_path, "needs investigation" if windows
