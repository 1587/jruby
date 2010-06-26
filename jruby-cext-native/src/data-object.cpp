/*
 * Copyright (C) 2009 Wayne Meissner
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
#include "jruby.h"
#include "JavaException.h"
#include "ruby.h"
#include "JLocalEnv.h"
#include "Handle.h"

using namespace jruby;
static void rubydata_finalize(Handle *);

class DataHandle: public Handle {
public:
    virtual ~DataHandle();
    virtual void mark();

    void (*dmark)(void *);
    void (*dfree)(void *);
    void* data;
};

extern "C" VALUE
rb_data_object_alloc(VALUE klass, void* data, RUBY_DATA_FUNC dmark, RUBY_DATA_FUNC dfree)
{
    DataHandle* h = new DataHandle;
    JLocalEnv env;
    
    h->data = data;
    h->dmark = dmark;
    h->dfree = dfree;
    h->finalize = rubydata_finalize;
    h->type = T_DATA;

    jvalue params[3];
    params[0].l = getRuntime();
    params[1].l = valueToObject(env, klass);
    params[2].j = p2j(h);

    jobject obj = env->CallStaticObjectMethodA(RubyData_class, RubyData_newRubyData_method, params);
    checkExceptions(env);

    h->obj = env->NewWeakGlobalRef(obj);
    checkExceptions(env);
    

    return (VALUE) (uintptr_t) h;
}

DataHandle::~DataHandle()
{
}

void DataHandle::mark()
{
    if (*dmark) {
        (*dmark)(data);
    }
}

extern "C" void*
jruby_data(VALUE v)
{
    if (TYPE(v) != T_DATA) {
        rb_raise(rb_eTypeError, "not a data object");
        return NULL;
    }

    return ((DataHandle *) v)->data;
}

static void
rubydata_finalize(Handle *h)
{
    DataHandle* dh = (DataHandle *) h;
    
    if (dh->dfree == (void *) -1) {
        xfree(dh->data);
    } else if (dh->dfree != NULL) {
        (*dh->dfree)(dh->data);
    }
}
