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
DataSyncQueue jruby::nsyncq = TAILQ_HEAD_INITIALIZER(nsyncq);
DataSyncQueue jruby::jsyncq = TAILQ_HEAD_INITIALIZER(jsyncq);
DataSyncQueue jruby::cleanq = TAILQ_HEAD_INITIALIZER(cleanq);

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
    setType(type_);
    this->obj = env->NewGlobalRef(obj_);
}

Handle::~Handle()
{
}

void
Handle::Init()
{
    flags = 0;
    setType(T_NONE);
    TAILQ_INSERT_TAIL(&liveHandles, this, all);
    
    if (++allocCount > GC_THRESHOLD) {
        allocCount = 0;
        JLocalEnv env;
        env->CallStaticVoidMethod(GC_class, GC_trigger);
    }
}

Handle*
Handle::specialHandle(VALUE v)
{
    if (likely((RSHIFT(v, 1) & ~3UL) == 0)) {
        return jruby::constHandles[(v >> 1) & 0x3UL];

    }

    rb_raise(rb_eTypeError, "%llx is not a valid handle", (unsigned long long) v);
    return NULL;
}

void
Handle::makeStrong_(JNIEnv* env)
{
    flags |= FL_WEAK;
    jobject tmp = env->NewGlobalRef(obj);
    env->DeleteWeakGlobalRef(obj);
    obj = tmp;
}

RubyFixnum::RubyFixnum(JNIEnv* env, jobject obj_, jlong value_): Handle(env, obj_, T_FIXNUM)
{
    this->value = value_;
}


RubyString::RubyString(JNIEnv* env, jobject obj_): Handle(env, obj_, T_STRING)
{
    rwdata.rstring = NULL;
}

RubyString::~RubyString()
{
}

int
RubyString::length()
{
    // If already synced with java, just return the cached length value
    if (rwdata.rstring != NULL) {
        return rwdata.rstring->len;
    }
    
    JLocalEnv env;

    jobject byteList = env->GetObjectField(obj, RubyString_value_field);

    return env->GetIntField(byteList, ByteList_length_field);
}

static bool
RubyString_jsync(JNIEnv* env, DataSync* data)
{
    return ((RubyString *) data->data)->jsync(env);
}

static bool
RubyString_nsync(JNIEnv* env, DataSync* data)
{
    return ((RubyString *) data->data)->nsync(env);
}

static bool
RubyString_clean(JNIEnv* env, DataSync* data)
{
    return ((RubyString *) data->data)->clean(env);
}

RString*
RubyString::toRString(bool readonly)
{
    if (rwdata.rstring != NULL) {
        if (readonly || !rwdata.readonly) {
            return rwdata.rstring;
        }

        // Switch from readonly to read-write
        rwdata.readonly = false;
        TAILQ_INSERT_TAIL(&jruby::nsyncq, &rwdata.nsync, syncq);
        JLocalEnv env;
        nsync(env);

        return rwdata.rstring;
    }

    JLocalEnv env;
    rwdata.jsync.data = this;
    rwdata.jsync.sync = RubyString_jsync;
    rwdata.nsync.data = this;
    rwdata.nsync.sync = RubyString_nsync;
    rwdata.clean.data = this;
    rwdata.clean.sync = RubyString_clean;
    rwdata.rstring = (RString *) j2p(env->CallStaticLongMethod(JRuby_class, JRuby_getRString, obj));
    rwdata.readonly = readonly;

    TAILQ_INSERT_TAIL(&jruby::cleanq, &rwdata.clean, syncq);
    TAILQ_INSERT_TAIL(&jruby::jsyncq, &rwdata.jsync, syncq);
    if (!readonly) {
        TAILQ_INSERT_TAIL(&jruby::nsyncq, &rwdata.nsync, syncq);
    }
    nsync(env);

    return rwdata.rstring;
}

bool
RubyString::clean(JNIEnv* env)
{
    // Clear the cached data
    rwdata.readonly = false;
    rwdata.rstring = NULL;

    return false;
}

bool
RubyString::jsync(JNIEnv* env)
{
    if (rwdata.readonly && rwdata.rstring != NULL) {
        // Don't sync anything, just clear the cached data
        rwdata.rstring = NULL;
        rwdata.readonly = false;

        return false;
    }

    if (rwdata.rstring != NULL && rwdata.rstring->ptr != NULL) {
        jobject byteList = env->GetObjectField(obj, RubyString_value_field);
        jobject bytes = env->GetObjectField(byteList, ByteList_bytes_field);
        jint begin = env->GetIntField(byteList, ByteList_begin_field);
        
        env->DeleteLocalRef(byteList);

        RString* rstring = rwdata.rstring;
        env->SetByteArrayRegion((jbyteArray) bytes, begin, rstring->len,
                (jbyte *) rstring->ptr);
        
        env->DeleteLocalRef(bytes);
    }

    return true;
}

bool
RubyString::nsync(JNIEnv* env)
{
    jobject byteList = env->GetObjectField(obj, RubyString_value_field);
    jobject bytes = env->GetObjectField(byteList, ByteList_bytes_field);
    jint begin = env->GetIntField(byteList, ByteList_begin_field);
    long length = env->GetIntField(byteList, ByteList_length_field);
    env->DeleteLocalRef(byteList);

    RString* rstring = rwdata.rstring;

    jint capacity = env->GetArrayLength((jarray) bytes);
    if (capacity > rstring->capa) {
        rstring->capa = capacity;
        rstring->ptr = (char *) realloc(rstring->ptr, rstring->capa + 1);
    }
    
    env->GetByteArrayRegion((jbyteArray) bytes, begin, length, 
            (jbyte *) rstring->ptr);
    env->DeleteLocalRef(bytes);

    rstring->ptr[rstring->len = length] = 0;

    return true;
}

RubyArray::RubyArray(JNIEnv* env, jobject obj_): Handle(env, obj_, T_ARRAY)
{
    rarray.ptr = NULL;
}

RubyArray::~RubyArray()
{
    if (rarray.ptr) {
        free(rarray.ptr);
    }
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

        T(BIGNUM);
        T(NIL);
        T(TRUE);
        T(FALSE);
        T(SYMBOL);
        T(REGEXP);
        T(HASH);
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

        case org_jruby_runtime_ClassIndex_FLOAT:
            h = new RubyFloat(env, obj, env->GetDoubleField(obj, RubyFloat_value_field));
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

extern "C" JNIEXPORT jlong JNICALL
Java_org_jruby_cext_Native_newFloatHandle(JNIEnv* env, jobject self, jobject obj, jdouble value)
{
    return jruby::p2j(new RubyFloat(env, obj, value));
}

/*
 * Class:     org_jruby_cext_Native
 * Method:    freeHandle
 * Signature: (J)V
 */
extern "C" JNIEXPORT void
JNICALL Java_org_jruby_cext_Native_freeHandle(JNIEnv* env, jclass self, jlong address)
{
    Handle* h = reinterpret_cast<Handle*>(address);

    TAILQ_REMOVE(&liveHandles, h, all);

    if ((h->flags & FL_WEAK) != 0) {
        env->DeleteWeakGlobalRef(h->obj);
    } else {
        env->DeleteGlobalRef(h->obj);
    }

    delete h;
}


jobject
jruby::valueToObject(JNIEnv* env, VALUE v)
{
    if (FIXNUM_P(v)) {
        return fixnumToObject(env, v);
        
    } else if (SYMBOL_P(v)) {
        return idToObject(env, SYM2ID(v));
    }

    Handle* h = Handle::valueOf(v);
    if (likely((h->flags & FL_WEAK) == 0)) {
        return h->obj;
    }

    jobject obj = env->NewLocalRef(h->obj);
    if (unlikely(env->IsSameObject(obj, NULL))) {
        rb_raise(rb_eRuntimeError, "weak handle is null");
    }

    return obj;
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
jruby::runSyncQueue(JNIEnv *env, DataSyncQueue* q)
{
    DataSync* d;

    for (d = TAILQ_FIRST(q); d != TAILQ_END(q); ) {
        DataSync* next = TAILQ_NEXT(d, syncq);

        if (!(*d->sync)(env, d)) {
            TAILQ_REMOVE(q, d, syncq);
        }

        d = next;
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
    return p2j(calloc(1, sizeof(struct RString)));
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
    if (rstring != NULL) {
        if (rstring->ptr != NULL) {
            free(rstring->ptr);
        }

        free(rstring);
    }
}


