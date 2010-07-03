/*
 * Copyright (C) 2008-2010 Wayne Meissner
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
SyncQueue jruby::syncQueue = SIMPLEQ_HEAD_INITIALIZER(syncQueue);

static int allocCount;
static const int GC_THRESHOLD = 10000;

Handle::Handle()
{
    obj = NULL;
    Init();
}

Handle::Handle(JNIEnv* env, jobject obj_, int type_)
{
    Init();
    this->obj = env->NewGlobalRef(obj_);
    this->type = type_;
}

Handle::~Handle()
{
}

void
Handle::Init()
{
    flags = 0;
    type = T_NONE;
    TAILQ_INSERT_TAIL(&liveHandles, this, all);

    if (++allocCount > GC_THRESHOLD) {
        allocCount = 0;
        JLocalEnv env;
        env->CallStaticVoidMethod(GC_class, GC_trigger);
    }
}

void
Handle::jsync(JNIEnv* env)
{
}

void
Handle::nsync(JNIEnv* env)
{
}


RubyFixnum::RubyFixnum(JNIEnv* env, jobject obj_, jlong value_): Handle(env, obj_, T_FIXNUM)
{
    this->value = value_;
}


RubyString::RubyString(JNIEnv* env, jobject obj_): Handle(env, obj_, T_STRING)
{
    rstring = NULL;
}

RubyString::~RubyString()
{
}

RString*
RubyString::toRString(bool readonly)
{
    if (rstring != NULL) {
        return rstring;
    }
    
    SIMPLEQ_INSERT_TAIL(&syncQueue, this, syncq);
    flags |= FL_NSYNC | (!readonly ? FL_JSYNC : 0);

    JLocalEnv env;

    if (rstring == NULL) {
        jvalue param;
        param.l = obj;
        rstring = (RString *) j2p(env->CallStaticLongMethodA(JRuby_class, JRuby_getRString, &param));
        rstring->ptr = NULL;
    }

    nsync(env);

    return rstring;
}

void
RubyString::jsync(JNIEnv* env)
{
    if (rstring != NULL && rstring->ptr != NULL) {
        jobject byteList = env->GetObjectField(obj, RubyString_value_field);
        jobject bytes = env->GetObjectField(byteList, ByteList_bytes_field);
        jint begin = env->GetIntField(byteList, ByteList_begin_field);
        
        env->SetByteArrayRegion((jbyteArray) bytes, begin, rstring->length, (jbyte *) rstring->ptr);
        
        env->DeleteLocalRef(byteList);
        env->DeleteLocalRef(bytes);
    }
}

void
RubyString::nsync(JNIEnv* env)
{
    jobject byteList = env->GetObjectField(obj, RubyString_value_field);
    jobject bytes = env->GetObjectField(byteList, ByteList_bytes_field);
    jint begin = env->GetIntField(byteList, ByteList_begin_field);
    jint length = env->GetIntField(byteList, ByteList_length_field);

    rstring->ptr = (char *) realloc(rstring->ptr, length + 1);
    rstring->length = length;
    
    env->GetByteArrayRegion((jbyteArray) bytes, begin, length, (jbyte *) rstring->ptr);
    rstring->ptr[length] = 0;

    env->DeleteLocalRef(byteList);
    env->DeleteLocalRef(bytes);
}

RubyArray::RubyArray(JNIEnv* env, jobject obj_): Handle(env, obj_, T_ARRAY)
{
}

RubyArray::~RubyArray()
{
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_jruby_cext_Native_newHandle(JNIEnv* env, jobject self, jobject obj, jint type)
{
    Handle* h;
    switch (type) {
#define T(x) \
        case org_jruby_runtime_ClassIndex_##x: \
            h = new Handle(env, obj, T_##x); \
            break;
        T(FIXNUM);
        T(BIGNUM);
        T(NIL);
        T(TRUE);
        T(FALSE);
        T(SYMBOL);
        T(REGEXP);
        T(HASH);
        T(FLOAT);
        T(MODULE);
        T(CLASS);
        T(OBJECT);
        T(STRUCT);
        T(FILE);

        case org_jruby_runtime_ClassIndex_NO_INDEX:
            h = new Handle(env, obj, T_NONE);
            break;

        case org_jruby_runtime_ClassIndex_MATCHDATA:
            h = new Handle(env, obj, T_MATCH);
            break;

        case org_jruby_runtime_ClassIndex_STRING:
            h = new RubyString(env, obj);
            break;

        case org_jruby_runtime_ClassIndex_ARRAY:
            h = new RubyArray(env, obj);
            break;

        default:
            h = new Handle(env, obj, T_OBJECT);
            break;
    }

    return jruby::p2j(h);
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_jruby_cext_Native_newFixnumHandle(JNIEnv* env, jobject self, jobject obj, jlong value)
{
    return jruby::p2j(new RubyFixnum(env, obj, value));
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

void
jruby::jsync_(JNIEnv *env)
{
    Handle* h;

    SIMPLEQ_FOREACH(h, &syncQueue, syncq) {
        if ((h->flags & FL_JSYNC) != 0) {
            h->jsync(env);
        }
    }
}

void
jruby::nsync_(JNIEnv *env)
{
    Handle* h;

    SIMPLEQ_FOREACH(h, &syncQueue, syncq) {
        if ((h->flags & FL_NSYNC) != 0) {
            h->nsync(env);
        }
    }
}

/*
 * Class:     org_jruby_cext_Native
 * Method:    newRString
 * Signature: ()J
 */
extern "C" JNIEXPORT jlong JNICALL
Java_org_jruby_cext_Native_newRString(JNIEnv* env, jclass self)
{
    RString* rstring = new RString;
    rstring->ptr = NULL;
    rstring->length = -1;

    return p2j(rstring);
}

/*
 * Class:     org_jruby_cext_Native
 * Method:    freeRString
 * Signature: (J)V
 */
extern "C" JNIEXPORT void JNICALL
Java_org_jruby_cext_Native_freeRString(JNIEnv* env, jclass self, jlong address)
{
    RString* rstring = (RString *) j2p(address);

    if (rstring->ptr != NULL) {
        free(rstring->ptr);
    }

    delete rstring;
}


