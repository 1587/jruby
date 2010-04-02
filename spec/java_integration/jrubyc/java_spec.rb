require File.dirname(__FILE__) + "/../spec_helper"
require 'jruby'
require 'jruby/compiler'

describe "A Ruby class compiled by jrubyc" do
  describe "generating a Java stub" do
    def generate(script)
      node = JRuby.parse(script)
      # we use __FILE__ so there's something for it to read
      JRuby::Compiler::JavaGenerator.generate_java node, __FILE__
    end
    
    it "generates Java source" do
      script = generate("class Foo; end")
      script.should_not == nil
      script.classes[0].name.should == "Foo"
      java = script.classes[0].to_s

      # a few sanity checks for default behaviors
      java.should match /import org\.jruby\.Ruby;/
      java.should match /public class Foo {/
      java.should match /static {/
      java.should match /public Foo\(Ruby \w+, RubyClass \w+\)/
    end

    EMPTY_INITIALIZE_PATTERN =
      /public Foo\(\) {\s+this\(__ruby__, __metaclass__\);\s+RuntimeHelpers.invoke\(.*, this, "initialize"\);/
    OBJECT_INITIALIZE_PATTERN =
      /public Foo\(Object \w+\) {\s+this\(__ruby__, __metaclass__\);\s+IRubyObject \w+ = JavaUtil.convertJavaToRuby\(__ruby__, \w+\);\s+RuntimeHelpers.invoke\(.*, this, "initialize", .*\);/
    STRING_INITIALIZE_PATTERN =
      /public Foo\(String \w+\) {\s+this\(__ruby__, __metaclass__\);\s+IRubyObject \w+ = JavaUtil.convertJavaToRuby\(__ruby__, \w+\);\s+RuntimeHelpers.invoke\(.*, this, "initialize", .*\);/

    describe "with no initialize method" do
      it "generates a default constructor" do
        cls = generate("class Foo; end").classes[0]
        cls.constructor.should be false

        java = cls.to_s
        java.should match EMPTY_INITIALIZE_PATTERN
      end
    end

    describe "with an initialize method" do
      describe "with no arguments" do
        it "generates a default constructor" do
          cls = generate("class Foo; def initialize; end; end").classes[0]
          cls.constructor.should be true

          init = cls.methods[0]
          init.should_not be nil
          init.name.should == "initialize"
          init.constructor.should == true
          init.java_signature.should == nil
          init.args.length.should == 0
          
          java = init.to_s
          java.should match EMPTY_INITIALIZE_PATTERN
        end
      end

      describe "with one argument and no java_signature" do
        it "generates an (Object) constructor" do
          cls = generate("class Foo; def initialize(a); end; end").classes[0]
          cls.constructor.should be true

          init = cls.methods[0]
          init.name.should == "initialize"
          init.constructor.should == true
          init.java_signature.should == nil
          init.args.length.should == 1
          init.args[0].should == 'a'
          
          java = init.to_s
          java.should match OBJECT_INITIALIZE_PATTERN
        end
      end

      describe "with one argument and a java_signature" do
        it "generates a type-appropriate constructor" do
          cls = generate("class Foo; java_signature 'void Foo(String)'; def initialize(a); end; end").classes[0]
          cls.constructor.should be true

          init = cls.methods[0]
          init.name.should == "initialize"
          init.constructor.should == true
          init.java_signature.should_not == nil
          init.java_signature.to_s.should == "void Foo(String)"
          init.args.length.should == 1
          init.args[0].should == 'a'

          java = init.to_s
          java.should match STRING_INITIALIZE_PATTERN
        end
      end
    end
  end
end
