windows = RbConfig::CONFIG['host_os'] =~ /mswin|mingw/

exclude :test_bom_16be, "needs investigation"
exclude :test_bom_16le, "needs investigation"
exclude :test_bom_32be, "needs investigation"
exclude :test_bom_32le, "needs investigation"
exclude :test_bom_8, "needs investigation"
exclude :test_each_byte_extended_file, "needs investigation"
exclude :test_each_char_extended_file, "needs investigation"
exclude :test_eof_0, "needs investigation"
exclude :test_eof_0_seek, "needs investigation"
exclude :test_eof_1, "needs investigation"
exclude :test_getbyte_extended_file , "needs investigation"
exclude :test_getc_extended_file , "needs investigation"
exclude :test_gets_extended_file , "needs investigation"
exclude :test_gets_para_extended_file , "needs investigation"
exclude :test_chmod_m17n, "needs investigation" if windows
exclude :test_read_all_extended_file , "needs investigation"
exclude :test_realdirpath , "needs investigation"
exclude :test_realpath , "needs investigation"
exclude :test_s_chown , "needs investigation"
exclude :test_truncate_wbuf, "fails on Linux"
exclude :test_uninitialized , "needs investigation"
exclude :test_unlink_before_close, "needs investigation" if windows
