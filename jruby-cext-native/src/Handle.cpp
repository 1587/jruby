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

#include <jni.h>
#include "JUtil.h"
#include "Handle.h"
#include "jruby.h"
#include "ruby.h"
#include "JLocalEnv.h"
#include "org_jruby_cext_Native.h"
#include "JavaException.h"
#include "org_jruby_runtime_ClassIndex.h"

using namespace jruby;

Handle* jruby::constHandles[3];
HandleList jruby::liveHandles = TAILQ_HEAD_INITIALIZER(liveHandles);
HandleList jruby::deadHandles = TAILQ_HEAD_INITIALIZER(deadHandles);
static int allocCount;
static const int GC_THRESHOLD = 10000;

Handle::Handle()
{
    obj = NULL;
    Init();
}

Handle::Handle(JNIEnv* env, jobject obj_)
{
    this->obj = env->NewGlobalRef(obj_);
    Init();
}

Handle::~Handle()
{
}

void
Handle::Init()
{
    flags = 0;
    type = T_NONE;
    finalize = NULL;
    TAILQ_INSERT_TAIL(&liveHandles, this, all);

    if (++allocCount > GC_THRESHOLD) {
        allocCount = 0;
        JLocalEnv env;
        env->CallStaticVoidMethod(GC_class, GC_trigger);
    }
}

Fixnum::Fixnum(JNIEnv* env, jobject obj_, jlong value_): Handle(env, obj_)
{
    this->value = value_;
    this->type = T_FIXNUM;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_jruby_cext_Native_newHandle(JNIEnv* env, jobject self, jobject obj)
{
    return jruby::p2j(new Handle(env, obj));
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_jruby_cext_Native_newFixnumHandle(JNIEnv* env, jobject self, jobject obj, jlong value)
{
    return jruby::p2j(new Fixnum(env, obj, value));
}

jobject
jruby::valueToObject(JNIEnv* env, VALUE v)
{
    return env->NewLocalRef(Handle::valueOf(v)->obj);
}

VALUE
jruby::objectToValue(JNIEnv* env, jobject obj)
{
    // Should never get null from JRuby, but check it anyway
    if (env->IsSameObject(obj, NULL)) {
    
        return Qnil;
    }

    jobject handleObject = env->CallStaticObjectMethod(Handle_class, Handle_valueOf, obj);
    checkExceptions(env);

    VALUE v = (VALUE) env->GetLongField(handleObject, Handle_address_field);
    checkExceptions(env);
    
    env->DeleteLocalRef(handleObject);

    return v;
}

