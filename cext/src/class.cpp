/*
 * Copyright (C) 2008, 2009 Wayne Meissner
 *
 * This file is part of jruby-cext.
 *
 * This code is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this work.  If not, see <http://www.gnu.org/licenses/>.
 */

#include "jruby.h"
#include "ruby.h"
#include "JLocalEnv.h"
#include "JUtil.h"


using namespace jruby;

static jobject getNotAllocatableAllocator(JNIEnv* env);
static jobject getDefaultAllocator(JNIEnv* env, VALUE parent);

extern "C" VALUE 
rb_class_new_instance(int arg_count, VALUE* args, VALUE class_handle) {
    return rb_funcall2(class_handle, rb_intern("new"), arg_count, args);
}

extern "C" VALUE 
rb_class_of(VALUE object_handle) {
    return rb_funcall(object_handle, rb_intern("class"), 0);
}

extern "C" VALUE 
rb_class_name(VALUE class_handle) {
    return rb_funcall(class_handle, rb_intern("name"), 0);
}

extern "C" char* 
rb_class2name(VALUE class_handle) {
    return (char*)RSTRING_PTR(rb_class_name(class_handle));
}

extern "C" VALUE
rb_cvar_defined(VALUE module_handle, ID name) {
    return rb_funcall(module_handle, rb_intern("class_variable_defined?"), 1, name);
}

extern "C" VALUE
rb_cv_get(VALUE module_handle, const char* name) {
    return rb_cvar_get(module_handle, rb_intern(name));
}

extern "C" VALUE
rb_cv_set(VALUE module_handle, const char* name, VALUE value) {
    return rb_cvar_set(module_handle, rb_intern(name), value, 0);
}

extern "C" VALUE
rb_cvar_get(VALUE module_handle, ID name) {
    return rb_funcall(module_handle, rb_intern("class_variable_get"), 1, name);
}

extern "C" VALUE
rb_cvar_set(VALUE module_handle, ID name, VALUE value, int unused) {
    return rb_funcall(module_handle, rb_intern("class_variable_set"), 2, name, value);
}

extern "C" void 
rb_define_class_variable(VALUE klass, const char* name, VALUE val) {
    rb_cvar_set(klass, rb_intern(name), val, 0);
}

extern "C" VALUE
rb_define_class(const char* name, VALUE parent)
{
    JLocalEnv env;
    
    jmethodID defineClass = getMethodID(env, Ruby_class, "defineClass",
            "(Ljava/lang/String;Lorg/jruby/RubyClass;Lorg/jruby/runtime/ObjectAllocator;)Lorg/jruby/RubyClass;");
    jobject result = env->CallObjectMethod(getRuntime(), defineClass,
            env->NewStringUTF(name), valueToObject(env, parent), getDefaultAllocator(env, parent));
    checkExceptions(env);
    
    return objectToValue(env, result);
}

extern "C" VALUE
rb_define_class_under(VALUE module, const char* name, VALUE parent)
{
    JLocalEnv env;

    jmethodID Ruby_defineClass_method = getMethodID(env, Ruby_class, "defineClassUnder",
            "(Ljava/lang/String;Lorg/jruby/RubyClass;Lorg/jruby/runtime/ObjectAllocator;Lorg/jruby/RubyModule;)Lorg/jruby/RubyClass;");

    jobject result = env->CallObjectMethod(getRuntime(), Ruby_defineClass_method,
            env->NewStringUTF(name), valueToObject(env, parent), getDefaultAllocator(env, parent),
            valueToObject(env, module));
    checkExceptions(env);

    return objectToValue(env, result);
}

extern "C" void
rb_define_alloc_func(VALUE klass, VALUE (*fn)(VALUE))
{
    JLocalEnv env;

    jobject allocator = env->NewObject(NativeObjectAllocator_class,
            getMethodID(env, NativeObjectAllocator_class, "<init>", "(J)V"),
            p2j((void *) fn));

    checkExceptions(env);

    jmethodID RubyClass_setAllocator_method = getMethodID(env, RubyClass_class,
            "setAllocator", "(Lorg/jruby/runtime/ObjectAllocator;)V");

    env->CallVoidMethod(valueToObject(env, klass), RubyClass_setAllocator_method, allocator);
}


static jobject
getNotAllocatableAllocator(JNIEnv* env)
{
    jfieldID NotAllocatableAllocator_field = env->GetStaticFieldID(ObjectAllocator_class, "NOT_ALLOCATABLE_ALLOCATOR",
            "Lorg/jruby/runtime/ObjectAllocator;");
    checkExceptions(env);
    jobject allocator = env->GetStaticObjectField(ObjectAllocator_class, NotAllocatableAllocator_field);
    checkExceptions(env);

    return allocator;
}

static jobject
getDefaultAllocator(JNIEnv* env, VALUE parent)
{
    jobject allocator = env->CallObjectMethod(valueToObject(env, parent),
            getMethodID(env, RubyClass_class, "getAllocator", "()Lorg/jruby/runtime/ObjectAllocator;"));
    checkExceptions(env);

    return allocator;
}