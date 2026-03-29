package com.close.hook.ads.ui.activity

import android.os.Bundle
import com.close.hook.ads.ui.fragment.cloud.CloudRuleFragment

class CloudRuleActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, CloudRuleFragment())
                .commit()
        }
    }
}
