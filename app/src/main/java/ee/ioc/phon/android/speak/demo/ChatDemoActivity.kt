/*
 * Copyright 2016-2017, Institute of Cybernetics at Tallinn University of Technology
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

package ee.ioc.phon.android.speak.demo

import android.app.Activity
import android.content.ComponentName
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import android.preference.PreferenceManager
import android.speech.RecognizerIntent
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import ee.ioc.phon.android.speak.R
import ee.ioc.phon.android.speak.adapter.RecyclerViewAdapter
import ee.ioc.phon.android.speak.model.CallerInfo
import ee.ioc.phon.android.speak.utils.Utils
import ee.ioc.phon.android.speak.view.AbstractSpeechInputViewListener
import ee.ioc.phon.android.speak.view.SpeechInputView
import ee.ioc.phon.android.speechutils.editor.UtteranceRewriter
import ee.ioc.phon.android.speechutils.utils.IntentUtils
import ee.ioc.phon.android.speechutils.utils.JsonUtils
import org.json.JSONException
import java.util.*

/**
 * Simple chat style interface, which demonstrates how to use SpeechInputView.
 *
 * TODO: each list item should have at least 3 components: spoken input,
 * pretty-printed output (JSON, or parts of it), formal output (JSON that can be executed)
 */
class ChatDemoActivity : Activity(), RecyclerViewAdapter.ItemClickListener {

    private val mMatches = ArrayList<String>()
    private val contextPhrasesMap = HashMap<String, List<String>>()
    private val contextSuggestedResponsesMap = HashMap<String, List<String>>()

    private var mPrefs: SharedPreferences? = null
    private var mRes: Resources? = null

    private var suggestedRecyclerViewAdapter: RecyclerViewAdapter? = null

    val speechInputViewListener: SpeechInputView.SpeechInputViewListener
        get() = object : AbstractSpeechInputViewListener() {

            private var mRewriters: Iterable<UtteranceRewriter>? = null

            override fun onComboChange(language: String, service: ComponentName) {
                mRewriters = Utils.genRewriters(mPrefs, mRes, arrayOf("Base", "Commands"), language, service, componentName)
            }

            override fun onFinalResult(results: List<String>, bundle: Bundle) {
                if (!results.isEmpty()) {
                    val result = results[0]
                    //String resultPp = "voice command (the raw utterance)\n\n" + result;
                    mMatches.add(result)
                    updateListView(mMatches)
                    // TODO: store the JSON also in the list, so that it can be reexecuted later
                    IntentUtils.launchIfIntent(this@ChatDemoActivity, mRewriters, result)

                    val suggestedResponses = getSuggestedResponses(result)
                    if (!suggestedResponses.isEmpty()) {
                        // present list of suggested responses to user
                        updateRecyclerView(suggestedResponses)
                    }
                }
            }

            override fun onError(errorCode: Int) {
                mMatches.add("* ERROR: $errorCode")
                updateListView(mMatches)
            }

            override fun onStartListening() {
                // stopTts();
            }
        }

    private fun getSuggestedResponses(speakerText: String): List<String> {
        var suggestedResponses: List<String> = ArrayList()
        val iterator = contextPhrasesMap.iterator()
        var speakerContext = ""
        if (speakerText.isNotBlank()) {
            iterator.forEach {
                if (it.value.contains(speakerText)) {
                    speakerContext = it.key
                }
            }
            if (speakerContext.isNotBlank()) {
                suggestedResponses = contextSuggestedResponsesMap[speakerContext] ?: ArrayList()
            }
        }
        return suggestedResponses
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_demo)

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
        mRes = resources

        val siv = findViewById<SpeechInputView>(R.id.vSpeechInputView)
        val callerInfo = CallerInfo(createExtras(), callingActivity)
        // TODO: review this
        siv.init(R.array.keysActivity, callerInfo, 0)
        siv.setListener(speechInputViewListener, null)

        initializeContextPhrasesAndResponses()

        (findViewById(R.id.list_matches) as ListView).onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val entry = parent.adapter.getItem(position)
            startActivity(entry.toString())
        }

        findViewById<RecyclerView>(R.id.suggestedReponses).layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        suggestedRecyclerViewAdapter = RecyclerViewAdapter(ArrayList<String>())
        suggestedRecyclerViewAdapter!!.setClickListener(this)
        updateRecyclerView(ArrayList())
    }

    override fun onItemClick(view: View, position: Int) {
        suggestedRecyclerViewAdapter.let {
            val suggestedTextResponseClicked = it?.getItem(position)
            if (suggestedTextResponseClicked != null) {
                mMatches.add(suggestedTextResponseClicked)
                updateListView(mMatches)
                updateRecyclerView(ArrayList())
            }
        }
    }

    private fun startActivity(intentAsJson: String) {
        try {
            IntentUtils.startActivityIfAvailable(this, JsonUtils.createIntent(intentAsJson))
        } catch (e: JSONException) {
            toast(e.localizedMessage)
        }
    }

    private fun updateListView(list: List<String>) {
        findViewById<ListView>(R.id.list_matches).adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
    }

    private fun updateRecyclerView(list: List<String>) {
        suggestedRecyclerViewAdapter?.setMDataset(list)
        findViewById<RecyclerView>(R.id.suggestedReponses).adapter = suggestedRecyclerViewAdapter
    }

    private fun toast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun createExtras(): Bundle {
        val bundle = Bundle()
        bundle.putInt(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        return bundle
    }

    private fun initializeContextPhrasesAndResponses() {
        contextPhrasesMap["greetings"] = Arrays.asList("hello", "hi")
        contextPhrasesMap["introductions"] = Arrays.asList("how are you", "how's it going", "what's up")
        contextPhrasesMap["food service"] = Arrays.asList("what would you like to order")

        contextSuggestedResponsesMap["greetings"] = Arrays.asList("hello", "hi")
        contextSuggestedResponsesMap["introductions"] = Arrays.asList("good, you", "bad", "okay", "doing good, you", "i'm okay", "i'm doing well, how about you")
        contextSuggestedResponsesMap["food service"] = Arrays.asList("can i please get")
    }
}