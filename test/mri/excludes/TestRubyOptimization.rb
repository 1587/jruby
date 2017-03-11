exclude :test_fixnum_minus, "needs investigation"
exclude :test_fixnum_plus, "needs investigation"
exclude :test_opt_case_dispatch, "depends on RubyVM"
exclude :test_string_freeze, "frozen string literal should be a new object if String#freeze is redefined (#2156)"
exclude :test_string_freeze_block, "frozen string literal revert to a call if String#freeze is redefined (#2156)"
exclude :test_string_freeze_saves_memory, "depends on ObjectSpace#memsize_of"
exclude :test_tailcall, "needs investigation"
exclude :test_tailcall_inhibited_by_block, "depends on RubyVM"
exclude :test_tailcall_inhibited_by_rescue, "depends on RubyVM"
exclude :test_tailcall_with_block, "needs investigation"
