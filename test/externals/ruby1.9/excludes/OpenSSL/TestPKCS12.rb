windows = RbConfig::CONFIG['host_os'] =~ /mswin|mingw/

exclude :test_create_with_chain, 'needs investigation'
exclude :test_create_with_chain_decode, 'unimplemented'
exclude :test_create_with_bad_nid, 'unimplemented'
exclude :test_create_with_itr, 'unimplemented'
exclude :test_create_with_mac_itr, 'unimplemented'
exclude :test_create_no_pass, 'fails on Java 6: #628'
