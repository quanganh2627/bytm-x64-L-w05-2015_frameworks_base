/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "OpenGLRenderer"

#include <stdlib.h>

#include "Debug.h"
#include "FboCache.h"
#include "Properties.h"

namespace android {
namespace uirenderer {

///////////////////////////////////////////////////////////////////////////////
// Constructors/destructor
///////////////////////////////////////////////////////////////////////////////

FboCache::FboCache(): mMaxSize(DEFAULT_FBO_CACHE_SIZE) {
    char property[PROPERTY_VALUE_MAX];
    if (property_get(PROPERTY_FBO_CACHE_SIZE, property, NULL) > 0) {
        INIT_LOGD("  Setting fbo cache size to %s", property);
        mMaxSize = atoi(property);
    } else {
        INIT_LOGD("  Using default fbo cache size of %d", DEFAULT_FBO_CACHE_SIZE);
    }
}

FboCache::~FboCache() {
    clear();
}

///////////////////////////////////////////////////////////////////////////////
// Size management
///////////////////////////////////////////////////////////////////////////////

uint32_t FboCache::getSize() {
    return mCache.size();
}

uint32_t FboCache::getMaxSize() {
    return mMaxSize;
}

///////////////////////////////////////////////////////////////////////////////
// Caching
///////////////////////////////////////////////////////////////////////////////

void FboCache::clear() {
    for (size_t i = 0; i < mCache.size(); i++) {
#ifdef HWUI_IMG_FBO_CACHE_OPTIM
        const GLuint fbo = mCache.itemAt(i).fbo;
#else
        const GLuint fbo = mCache.itemAt(i);
#endif
        glDeleteFramebuffers(1, &fbo);
    }
    mCache.clear();
}

#ifdef HWUI_IMG_FBO_CACHE_OPTIM

GLuint FboCache::get(int w, int h) {
    GLuint fbo;
    for (int i = mCache.size() - 1; i >= 0; i--)
    {
        FboEntry entry = mCache.itemAt(i);
        if (entry.w == w && entry.h == h)
        {
            mCache.removeAt(i);
            return entry.fbo;
        }
    }

    glGenFramebuffers(1, &fbo);
    return fbo;
}

bool FboCache::put(GLuint fbo, int w, int h) {

    if (mCache.size() == mMaxSize) {
        FboEntry entry = mCache.itemAt(0);
        mCache.removeAt(0);
        glDeleteFramebuffers(1, &entry.fbo);
    }

    mCache.replaceAt(FboEntry(fbo, w, h),
                     mCache.insertAt(mCache.size()));
    return true;
}

#else /* HWUI_IMG_FBO_CACHE_OPTIM */

GLuint FboCache::get(int w, int h) {
    GLuint fbo;
    if (mCache.size() > 0) {
        fbo = mCache.itemAt(mCache.size() - 1);
        mCache.removeAt(mCache.size() - 1);
    } else {
       glGenFramebuffers(1, &fbo);
    }
    return fbo;
}

bool FboCache::put(GLuint fbo, int w, int h) {
    if (mCache.size() < mMaxSize) {
        mCache.add(fbo);
        return true;
    }

    glDeleteFramebuffers(1, &fbo);
    return false;
}

#endif /* HWUI_IMG_FBO_CACHE_OPTIM */

}; // namespace uirenderer
}; // namespace android
