const { expect } = require('chai');

const btnGammaDev = () => $('//*[@text="Gamma Dev"]');

const dialogBtn = text => () => $(`//android.widget.Button[@text="${text}"]`);
const dialogBtnContinue = dialogBtn('Continue');
const dialogBtnCancel = dialogBtn('Cancel');
const dialogTitle = () => $('//android.widget.TextView[@resource-id="android:id/message"]');

const txtBoxUsername = () => $('//android.widget.EditText[@resource-id="user"]');
const txtBoxPassword = () => $('//android.widget.EditText[@resource-id="password"]');
const btnLogin = () => $('//android.widget.Button[@resource-id="login"]');
const loginError = () => $('//android.widget.TextView[@text="Incorrect user name or password. Please try again."]');


describe('settings dialog', () => {
  it('connects to Gamma login screen', async () => {
    await btnGammaDev().click();
    const title = await dialogTitle().getText();
    expect(title).to.equal('Login to Gamma Dev?');
    expect(await dialogBtnContinue().isDisplayed()).to.be.true;
    expect(await dialogBtnCancel().isDisplayed()).to.be.true;

    await dialogBtnContinue().click();

    await btnLogin().waitForDisplayed();

    const locales = [
      '~Bamanankan (Bambara)',
      '~English',
      '~Español (Spanish)',
      '~Français (French)',
      '~हिन्दी (Hindi)',
      '~Bahasa Indonesia (Indonesian)',
      '~नेपाली (Nepali)',
      '~Kiswahili (Swahili)'
    ];
    for(const locale of locales) {
      expect(await $(locale).isDisplayed()).to.be.true
    }

    await $('~English').click();

    await txtBoxUsername().setValue('fakename');
    await txtBoxPassword().setValue('fake_password');
    await btnLogin().click();



// assuming we have an initialized `driver` object for an app
//     const contexts = await browser.getContexts();
//     await browser.switchContext('WEBVIEW_org.medicmobile.webapp.mobile');
    // browser.context(contexts[1]); // choose the webview context
        // do some web testing
    // browser.findElement()
    //     .elementsByCss('.green_button').click()
    //     .context('NATIVE_APP') // leave webview context
        // do more native stuff here if we want
        // .quit() // stop webdrivage

    // const loginError = () => $('p.error.incorrect');

    expect(await loginError().isDisplayed()).to.be.true;
  });
});
