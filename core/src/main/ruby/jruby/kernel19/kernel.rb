module Kernel
  module_function
  def require_relative(relative_arg)
    relative_arg = relative_arg.to_path if relative_arg.respond_to? :to_path
    relative_arg = JRuby::Type.convert_to_str(relative_arg)
    
    caller.first.rindex(/:\d+:in /) 
    file = $` # just the filename
    raise LoadError, "cannot infer basepath" if /\A\((.*)\)/ =~ file # eval etc.

    absolute_feature = File.expand_path(relative_arg, File.dirname(File.realpath(file)))
    
    require absolute_feature
  end

  def exec(*args)
    _exec_internal(*JRuby::ProcessUtil.exec_args(args))
  end
  
  def spawn(*args)
    Process.spawn(*args)
  end
end
