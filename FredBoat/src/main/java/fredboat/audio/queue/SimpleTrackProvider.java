/*
 * MIT License
 *
 * Copyright (c) 2017 Frederik Ar. Mikkelsen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package fredboat.audio.queue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SimpleTrackProvider extends AbstractTrackProvider {

    private volatile ConcurrentLinkedQueue<AudioTrackContext> queue = new ConcurrentLinkedQueue<>();
    private AudioTrackContext lastTrack = null;
    private List<AudioTrackContext> cachedShuffledQueue = new ArrayList<>();
    private boolean shouldUpdateShuffledQueue = true;

    @Override
    public AudioTrackContext getNext() {
        if (!isShuffle()) {
            return queue.peek();
        } else {
            return getAsListOrdered().get(0);
        }
    }

    @Override
    public AudioTrackContext provideAudioTrack(boolean skipped) {
        if (getRepeatMode() == RepeatMode.SINGLE && !skipped && lastTrack != null) {
            return lastTrack.makeClone();
        }
        if (getRepeatMode() == RepeatMode.ALL && lastTrack != null) {
            //add a fresh copy of the last track back to the queue, if the queue is being repeated
            AudioTrackContext clone = lastTrack.makeClone();
            if (isShuffle()) {
                clone.setRand(Integer.MAX_VALUE); //put it at the back of the shuffled queue
                shouldUpdateShuffledQueue = true;
            }
            queue.add(clone);
        }
        if (isShuffle()) {
            List<AudioTrackContext> list = getAsListOrdered();

            if (list.isEmpty()) {
                return null;
            }

            shouldUpdateShuffledQueue = true;
            lastTrack = list.get(0);
            queue.remove(lastTrack);
            return lastTrack;
        } else {
            lastTrack = queue.poll();
            return lastTrack;
        }
    }

    public boolean remove(AudioTrackContext atc) {
        if (queue.remove(atc)) {
            shouldUpdateShuffledQueue = true;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public AudioTrackContext removeAt(int i) {
        if (queue.size() < i) {
            return null;
        } else {
            int i2 = 0;
            for (AudioTrackContext obj : getAsListOrdered()) {
                if (i == i2) {
                    shouldUpdateShuffledQueue = true;
                    //noinspection SuspiciousMethodCalls
                    queue.remove(obj);
                    return obj;
                }
                i2++;
            }
        }

        return null;
    }

    @Override
    public List<AudioTrackContext> getInRange(int startIndex, int endIndex) {
        if (queue.size() < endIndex) {
            return new ArrayList<>();
        } else {
            int remain = endIndex - startIndex + 1;
            int i = 0;
            List<AudioTrackContext> atl = new ArrayList<>();
            for (AudioTrackContext obj : getAsListOrdered()) {
                if (i >= startIndex && remain > 0) {
                    shouldUpdateShuffledQueue = true;
                    atl.add(obj);
                    remain--;
                } else if (remain <= 0) {
                    return atl;
                }
                i++;
            }
        }

        return new ArrayList<>();
    }

    @Override
    public List<AudioTrackContext> getAsList() {
        return new ArrayList<>(queue);
    }

    @Override
    public void setShuffle(boolean shuffle) {
        super.setShuffle(shuffle);
        //this is needed because, related to the repeat all mode, turning shuffle off, skipping a track, turning shuffle
        //on will cause an incorrect playlist to show with the list command and may lead to a bug of an
        //IllegalStateException due to trying to play the same AudioTrack object twice
        if (shuffle) shouldUpdateShuffledQueue = true;
    }

    public synchronized void reshuffle() {
        queue.forEach(AudioTrackContext::randomize);
        shouldUpdateShuffledQueue = true;
    }

    @Override
    public synchronized List<AudioTrackContext> getAsListOrdered() {
        if (!isShuffle()) {
            return getAsList();
        }

        if (!shouldUpdateShuffledQueue) {
            return cachedShuffledQueue;
        }

        List<AudioTrackContext> newList = new ArrayList<>();

        //Update the new queue
        newList.addAll(getAsList());

        Collections.sort(newList);

        //adjust rand values so they are evenly spread out
        int i = 0;
        int size = newList.size();
        for (AudioTrackContext atc : newList) {
            //this will calculate a value between 0.0 < rand < 1.0 multiplied by the full integer range
            int rand = (int) (((i / (size + 1.0)) + (1.0 / (size + 1.0))) * Integer.MAX_VALUE);
            atc.setRand(rand);
            i++;
        }

        cachedShuffledQueue = newList;

        shouldUpdateShuffledQueue = false;
        return newList;
    }

    @Override
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public void add(AudioTrackContext track) {
        shouldUpdateShuffledQueue = true;
        queue.add(track);
    }

    @Override
    public void clear() {
        shouldUpdateShuffledQueue = true;
        queue.clear();
    }
}
