/*
 * Copyright (C) 1993-2003 Yukihiro Matsumoto
 * Copyright (C) 2000 Network Applied Communication Laboratory, Inc.
 * Copyright (C) 2000 Information-technology Promotion Agency, Japan
 * Copyright (C) 2010 Tim Felgentreff
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

#include "Handle.h"
#include "JavaException.h"
#include "JLocalEnv.h"
#include "ruby.h"
#include <errno.h>

using namespace jruby;

static int set_non_blocking(int fd);

struct RIO*
RubyIO::toRIO()
{
    JLocalEnv env;

    if (!rio.f) {
        char mode[4] = "\0";
        // open the file with the given descriptor in a compatible mode
        switch (rio.mode & FMODE_READWRITE) {
        case FMODE_READABLE:
            strcpy(mode, "rb");
        case FMODE_WRITABLE:
            strcpy(mode, "wb");
        case FMODE_READWRITE:
            strcpy(mode, "rb+");
        default:
            strcpy(mode, "rb+");
        }
        rio.f = fdopen(rio.fd, mode);
        if (!rio.f)
            throw JavaException(env, "java/lang/NullPointerException",
                "Invalid mode %s for %d (open with %d)", mode, rio.fd, rio.mode);
    }

    // If the file is not closed, sync
    if (rio.fd > -1) {
        /* TODO: Synchronization of stream positions
        long int cpos = ftell(rio.f);
        long long rpos = NUM2LL(callMethod(this, "pos", 0));
        callMethod
        */
    }

    return &rio;
}

RubyIO::RubyIO(FILE* native_file, int native_fd, int mode_)
{
    JLocalEnv env;
    setType(T_FILE);
    rio.fd = native_fd;
    rio.f = native_file;
    rio.mode = mode_;
    rio.obj = (VALUE)this;

    obj = valueToObject(env, callMethod(rb_cIO, "new", 2, INT2FIX(native_fd), INT2FIX(mode_)));
}

RubyIO::RubyIO(JNIEnv* env, jobject obj_, jint fileno, jint mode_): Handle(env, obj_, T_FILE) {
    rio.fd = (int)fileno;
    rio.f = NULL;
    rio.mode = (int)mode_;
    rio.obj = (VALUE)this;
}

RubyIO::~RubyIO() {
}

extern "C" rb_io_t*
jruby_io_struct(VALUE io)
{
    Handle* h = Handle::valueOf(io);
    if (h->getType() != T_FILE) {
        rb_raise(rb_eArgError, "Invalid type. Expected an object of type IO");
    }
    return ((RubyIO*) h)->toRIO();
}

static int
jruby_io_wait(int fd, int read)
{
    bool retry = false;

    if (fd < 0) {
        rb_raise(rb_eIOError, "closed stream");
    }

    switch(errno) {
    case EINTR:
#ifdef ERESTART
    case ERESTART:
#endif
        retry = true;
        break;

    case EAGAIN:
#if defined(EWOULDBLOCK) && EWOULDBLOCK != EAGAIN
    case EWOULDBLOCK:
#endif
        break;

    default:
        return Qfalse;
    }

    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(fd, &fds);

    int ready = 0;

    while (!ready) {
        if (read) {
            ready = rb_thread_select(fd+1, &fds, 0, 0, 0);
        } else {
            ready = rb_thread_select(fd+1, 0, &fds, 0, 0);
        }
        if (!retry) break;
    }

    return Qtrue;
}

extern "C" int
rb_io_wait_readable(int f)
{
    return jruby_io_wait(f, 1);
}

extern "C" int
rb_io_wait_writable(int f)
{
    return jruby_io_wait(f, 0);
}

/** Send #write to io passing str. */
extern "C" VALUE
rb_io_write(VALUE io, VALUE str)
{
    return callMethod(io, "write", 1, str);
}

extern "C" int
rb_io_fd(VALUE io)
{
    return jruby_io_struct(io)->fd;
}

extern "C" void
rb_io_set_nonblock(rb_io_t* io)
{
    set_non_blocking(io->fd);
}

extern "C" void
rb_io_check_readable(rb_io_t* io) {
    callMethod(io->obj, "read_nonblock", 1, rb_str_new_cstr(""));
}

extern "C" void
rb_io_check_writable(rb_io_t* io) {
    callMethod(io->obj, "write_nonblock", 1, rb_str_new_cstr(""));
}

extern "C" void
rb_io_check_closed(rb_io_t* io) {
    callMethod(io->obj, "closed?", 0);
}

static int set_non_blocking(int fd) {
  int flags;
#if defined(O_NONBLOCK)
  if (-1 == (flags = fcntl(fd, F_GETFL, 0)))
    flags = 0;
  return fcntl(fd, F_SETFL, flags | O_NONBLOCK);
#else
  flags = 1;
  return ioctl(fd, FIOBIO, &flags);
#endif
}
