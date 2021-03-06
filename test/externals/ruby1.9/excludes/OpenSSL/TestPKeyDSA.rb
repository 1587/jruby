windows = RbConfig::CONFIG['host_os'] =~ /mswin|mingw/

exclude :test_export_format_is_DSA_PUBKEY_pem, 'most likely \r\n' if windows
exclude :test_new, 'needs investigation'
exclude :test_private, 'needs investigation'
exclude :test_read_DSAPublicKey_pem, 'needs investigation'
exclude :test_read_private_key_pem_pw, 'needs investigation'
exclude :test_read_public_key_der, 'needs investigation'
exclude :test_read_public_key_pem, 'needs investigation'
exclude :test_sys_sign_verify, 'needs investigation'

