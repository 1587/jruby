windows = RbConfig::CONFIG['host_os'] =~ /mswin|mingw/

exclude :test_export_format_is_RSA_PUBKEY_pem, 'most likely \r\n' if windows
exclude :test_export_format_is_RSA_PUBKEY, 'needs investigation'
exclude :test_read_private_key_pem_pw, 'needs investigation'
exclude :test_read_public_key_pem, 'needs investigation'
