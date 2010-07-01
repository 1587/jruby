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

#ifndef JRUBY_HANDLE_H
#define	JRUBY_HANDLE_H

#include <jni.h>
#include "queue.h"
#include "ruby.h"

#ifdef	__cplusplus
extern "C" {
#endif

#define CONST_MASK (0x7UL)
#define IS_CONST(x) (((x) & ~CONST_MASK) == 0L)

namespace jruby {

    class Handle;
    extern Handle* constHandles[3];

    class Handle {
    public:
        Handle();
        virtual ~Handle();
        virtual void mark();

        static inline Handle* valueOf(VALUE v) {
            return !IS_CONST(v) ? (Handle *) v : jruby::constHandles[(v & CONST_MASK) >> 1];
        }

        inline void makeStrong(JNIEnv* env) {
            if (strongRef == NULL) {
                strongRef = env->NewGlobalRef(obj);
            }
        }

        inline void makeWeak(JNIEnv* env) {
            if (strongRef != NULL) {
                env->DeleteGlobalRef(strongRef);
                strongRef = NULL;
            }
        }


        jweak obj;
        jobject strongRef;
        int flags;
        int type;
        void (*finalize)(Handle *);
        TAILQ_ENTRY(Handle) all;
    };

    class Fixnum: public Handle {
    private:
        jlong value;
    public:
        Fixnum(jlong value_) {
            value = value_;
        }

        inline jlong longValue() {
            return value;
        }
    };

    class DataHandle: public Handle {
    public:
        virtual ~DataHandle();
        virtual void mark();
        TAILQ_ENTRY(DataHandle) dataList;
        void (*dmark)(void *);
        void (*dfree)(void *);
        void* data;
    };

    TAILQ_HEAD(HandleList, Handle);
    TAILQ_HEAD(DataHandleList, DataHandle);
    extern HandleList allHandles;
    extern DataHandleList dataHandles;
}
// FIXME - no need to match ruby here, unless we fold type into flags
#define FL_MARK      (1<<6)
#define FL_FINALIZE  (1<<7)
#define FL_TAINT     (1<<8)
#define FL_EXIVAR    (1<<9)
#define FL_FREEZE    (1<<10)

    
#ifdef	__cplusplus
}
#endif

#endif	/* JRUBY_HANDLE_H */

