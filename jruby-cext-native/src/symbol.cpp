/*
 * Copyright (C) 2010 Wayne Meissner
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

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pthread.h>
#include <sys/param.h>
#include <jni.h>

#include <map>

#include "JLocalEnv.h"
#include "jruby.h"
#include "Handle.h"
#include "JUtil.h"
#include "ruby.h"



using namespace jruby;

static std::map<const char*, ID> constSymbolMap;
std::vector<RubySymbol *> jruby::symbols;

static RubySymbol* lookup(ID id);
static RubySymbol* addSymbol(JNIEnv* env, jobject obj, ID id);

extern "C" JNIEXPORT jlong JNICALL
Java_org_jruby_cext_Native_newSymbolHandle(JNIEnv* env, jobject self, jobject obj)
{
    return p2j(addSymbol(env, obj, env->GetIntField(obj, RubySymbol_id_field)));
}

RubySymbol*
RubySymbol::valueOf(ID id)
{
    RubySymbol* s;
    if ((s = lookup(id)) != NULL) {
        return s;
    }

    rb_raise(rb_eNameError, "could not locate symbol for id %ld", id);

    return NULL;
}

extern "C" ID
rb_intern_const(const char* name)
{
    std::map<const char*, ID>::iterator it = constSymbolMap.find(name);
    if (it != constSymbolMap.end()) {
        return it->second;
    }

    return constSymbolMap[name] = jruby_intern_nonconst(name);
}

extern "C" ID
jruby_intern_nonconst(const char* name)
{
    JLocalEnv env;
    jobject result = env->CallObjectMethod(getRuntime(), Ruby_newSymbol_method, env->NewStringUTF(name));
    checkExceptions(env);

    ID id = env->GetIntField(result, RubySymbol_id_field);

    addSymbol(env, result, id);
    
    return id;
}

static RubySymbol*
lookup(ID id)
{
    return id < symbols.size() ? symbols[id] : NULL;
}

static RubySymbol*
addSymbol(JNIEnv* env, jobject obj, ID id)
{
    RubySymbol* s;
    if ((s = lookup(id)) != NULL) {
        return s;
    }

    s = new RubySymbol(env, obj, id);
    if (symbols.size() <= id) {
        symbols.resize(id + 1);
    }
    symbols[id] = s;

    return s;
}
