/*
 * Copyright 2021 Ona Systems, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.smartregister.fhircore.eir.ui.adverseevent

import android.app.AlertDialog
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import ca.uhn.fhir.context.FhirContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.hl7.fhir.r4.model.Immunization
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.smartregister.fhircore.eir.R
import org.smartregister.fhircore.eir.data.PatientRepository
import org.smartregister.fhircore.eir.ui.patient.register.PatientItemMapper
import org.smartregister.fhircore.engine.configuration.app.ConfigurableApplication
import org.smartregister.fhircore.engine.ui.base.AlertDialogue
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireActivity
import org.smartregister.fhircore.engine.ui.questionnaire.QuestionnaireViewModel
import org.smartregister.fhircore.engine.util.extension.createFactory
import timber.log.Timber

class AdverseEventQuestionnaireActivity : QuestionnaireActivity() {

  override fun createViewModel(application: Application): QuestionnaireViewModel {
    return ViewModelProvider(
        this@AdverseEventQuestionnaireActivity,
        AdverseEventViewModel(
            application,
            PatientRepository(
              (application as ConfigurableApplication).fhirEngine,
              PatientItemMapper
            )
          )
          .createFactory()
      )
      .get(AdverseEventViewModel::class.java)
  }

  override fun handleQuestionnaireResponse(questionnaireResponse: QuestionnaireResponse) {
    lifecycleScope.launch {
      immunizationId?.let { immunizationId ->
        (questionnaireViewModel as AdverseEventViewModel).loadImmunization(immunizationId).observe(
            this@AdverseEventQuestionnaireActivity
          ) { oldImmunization ->
          if (oldImmunization != null) {
            lifecycleScope.launch {
              questionnaire.let { questionnaire ->
                val alertDialog =
                  AlertDialogue.showProgressAlert(
                    this@AdverseEventQuestionnaireActivity,
                    R.string.loading
                  )
                questionnaireViewModel.extractionProgress.observe(
                  this@AdverseEventQuestionnaireActivity,
                  { result ->
                    if (result) {
                      alertDialog.dismiss()
                      finish()
                    } else {
                      Timber.e("An error occurred during extraction")
                    }
                  }
                )

                questionnaireViewModel.performExtraction(
                    questionnaire,
                    questionnaireResponse,
                    this@AdverseEventQuestionnaireActivity
                  )
                  .run {
                    val immunizationEntry = entry.firstOrNull { it.resource is Immunization }
                    if (immunizationEntry == null) {
                      val fhirJsonParser = FhirContext.forR4().newJsonParser()
                      Timber.e(
                        "Immunization extraction failed for ${fhirJsonParser.encodeResourceToString(questionnaireResponse)} producing ${fhirJsonParser.encodeResourceToString(this)}"
                      )
                      lifecycleScope.launch(Dispatchers.Main) { handleExtractionError() }
                    } else {
                      (immunizationEntry.resource as Immunization).reaction.addAll(
                        oldImmunization.reaction
                      )
                      questionnaireViewModel.saveBundleResources(this)
                    }
                  }
              }
            }
          }
        }
      }
    }
  }

  private fun handleExtractionError() {
    AlertDialog.Builder(this)
      .setTitle(getString(R.string.error_reading_immunization_details))
      .setMessage(getString(R.string.kindly_retry_contact_devs_problem_persists))
      .setPositiveButton(android.R.string.ok) { dialogInterface, _ -> dialogInterface.dismiss() }
      .setCancelable(true)
      .show()
  }
}