import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import { mockApi, renderApp, stubDownloads, USER } from '../test/helpers'

const NO_CV_USER = { ...USER, hasMainCv: false, mainCvFileName: null }
const CV_LABEL = 'Upload CV (PDF, DOCX or TXT)'

describe('ProfilePage account', () => {
  test('GivenUser_WhenOpening_ThenNameAndEmailAreShown', () => {
    mockApi({})
    renderApp('/profile')

    expect(screen.getByRole('heading', { name: 'Profile' })).toBeInTheDocument()
    expect(screen.getAllByText('Dana Levi').length).toBeGreaterThan(0)
    expect(within(screen.getByRole('main')).getByText('dana@example.com')).toBeInTheDocument()
  })

  test('GivenNewName_WhenUpdating_ThenServerGetsOldAndNewNameAndNavigationShowsIt', async () => {
    const api = mockApi({ 'PUT /api/users/1/name': { body: { ...USER, name: 'Dana L' } } })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('New name'), 'Dana L')
    await user.click(screen.getByRole('button', { name: 'Update name' }))

    expect(await screen.findByText('Name updated.')).toBeInTheDocument()
    expect(api.to('PUT /api/users/1/name')[0].body).toEqual({ oldValue: 'Dana Levi', newValue: 'Dana L' })
    expect(screen.getAllByText('Dana L').length).toBeGreaterThan(0)
    expect(JSON.parse(localStorage.getItem('ats.session')!).user.name).toBe('Dana L')
    expect(screen.getByLabelText('New name')).toHaveValue('')
  })

  test('GivenNewEmail_WhenUpdating_ThenServerGetsOldAndNewEmail', async () => {
    const api = mockApi({ 'PUT /api/users/1/email': { body: { ...USER, email: 'new@example.com' } } })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('New email'), 'new@example.com')
    await user.click(screen.getByRole('button', { name: 'Update email' }))

    expect(await screen.findByText('Email updated.')).toBeInTheDocument()
    expect(api.to('PUT /api/users/1/email')[0].body).toEqual({ oldValue: 'dana@example.com', newValue: 'new@example.com' })
    expect(within(screen.getByRole('main')).getByText('new@example.com')).toBeInTheDocument()
  })

  test('GivenEmailOfAnotherUser_WhenUpdating_ThenConflictMessageIsShown', async () => {
    mockApi({ 'PUT /api/users/1/email': { status: 409, body: { message: 'Email already registered' } } })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('New email'), 'taken@example.com')
    await user.click(screen.getByRole('button', { name: 'Update email' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Email already registered')
    expect(within(screen.getByRole('main')).getByText('dana@example.com')).toBeInTheDocument()
  })
})

describe('ProfilePage password', () => {
  test('GivenWeakNewPassword_WhenChanging_ThenRuleIsShownAndNothingIsSent', async () => {
    const api = mockApi({})
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('Current password'), 'secret123')
    await user.type(screen.getByLabelText('New password'), 'weak')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('at least 7 characters')
    expect(api.calls).toHaveLength(0)
  })

  test('GivenWrongCurrentPassword_WhenChanging_ThenServerMessageIsShownAndSessionStays', async () => {
    mockApi({
      'PUT /api/users/1/password': { status: 400, body: { message: 'Old password does not match current password.' } },
    })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('Current password'), 'wrongold1')
    await user.type(screen.getByLabelText('New password'), 'brandnew123')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Old password does not match')
    expect(localStorage.getItem('ats.session')).not.toBeNull()
  })

  test('GivenRightCurrentPassword_WhenChanging_ThenUserIsSignedOutWithNotice', async () => {
    const api = mockApi({ 'PUT /api/users/1/password': { body: USER } })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('Current password'), 'secret123')
    await user.type(screen.getByLabelText('New password'), 'brandnew123')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Password changed. Please sign in again.')
    expect(api.to('PUT /api/users/1/password')[0].body).toEqual({ oldValue: 'secret123', newValue: 'brandnew123' })
    expect(localStorage.getItem('ats.session')).toBeNull()
  })
})

describe('ProfilePage main CV', () => {
  test('GivenNoCv_WhenOpening_ThenMessageIsShownAndNoDownloadButtons', () => {
    mockApi({})
    renderApp('/profile', { user: NO_CV_USER })

    expect(screen.getByText(/You have not uploaded one yet/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Download CV' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Show extracted text' })).not.toBeInTheDocument()
  })

  test('GivenCv_WhenOpening_ThenFileNameAndButtonsAreShown', () => {
    mockApi({})
    renderApp('/profile')

    expect(screen.getByText(/Current file: cv.pdf/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download CV' })).toBeInTheDocument()
  })

  test('GivenFile_WhenUploading_ThenUserIsUpdatedAndButtonsAppear', async () => {
    const api = mockApi({
      'PUT /api/users/1/cv': { body: { ...USER, hasMainCv: true, mainCvFileName: 'new.txt', importedSkills: 0 } },
    })
    const user = userEvent.setup()
    renderApp('/profile', { user: NO_CV_USER })

    await user.upload(screen.getByLabelText(CV_LABEL), new File(['my cv'], 'new.txt', { type: 'text/plain' }))

    expect(await screen.findByText('Main CV uploaded.')).toBeInTheDocument()
    expect(screen.getByText(/Current file: new.txt/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download CV' })).toBeInTheDocument()
    expect(((api.to('PUT /api/users/1/cv')[0].body as FormData).get('file') as File).name).toBe('new.txt')
    expect(JSON.parse(localStorage.getItem('ats.session')!).user.hasMainCv).toBe(true)
  })

  test('GivenCvWithSkills_WhenUploading_ThenUserIsToldHowManySkillsWereAdded', async () => {
    mockApi({
      'PUT /api/users/1/cv': { body: { ...USER, hasMainCv: true, mainCvFileName: 'new.txt', importedSkills: 5 } },
    })
    const user = userEvent.setup()
    renderApp('/profile', { user: NO_CV_USER })

    await user.upload(screen.getByLabelText(CV_LABEL), new File(['Java Docker'], 'new.txt', { type: 'text/plain' }))

    const notice = (await screen.findByText(/We found 5 skills in your CV and added them/)).closest('p')!
    expect(within(notice).getByRole('link', { name: 'Skills' })).toHaveAttribute('href', '/skills')
  })

  test('GivenCvWithoutKnownSkills_WhenUploading_ThenNoImportMessageIsShown', async () => {
    mockApi({
      'PUT /api/users/1/cv': { body: { ...USER, hasMainCv: true, mainCvFileName: 'new.txt', importedSkills: 0 } },
    })
    const user = userEvent.setup()
    renderApp('/profile', { user: NO_CV_USER })

    await user.upload(screen.getByLabelText(CV_LABEL), new File(['bread'], 'new.txt', { type: 'text/plain' }))

    expect(await screen.findByText('Main CV uploaded.')).toBeInTheDocument()
    expect(screen.queryByText(/We found/)).not.toBeInTheDocument()
  })

  test('GivenUploadRejected_WhenUploading_ThenErrorIsShown', async () => {
    mockApi({ 'PUT /api/users/1/cv': { status: 500, body: {} } })
    const user = userEvent.setup()
    renderApp('/profile', { user: NO_CV_USER })

    await user.upload(screen.getByLabelText(CV_LABEL), new File(['x'], 'cv.txt', { type: 'text/plain' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The server hit an unexpected error')
  })

  test('GivenCv_WhenDownloading_ThenFileIsFetchedAndSaved', async () => {
    const downloads = stubDownloads()
    const api = mockApi({
      'GET /api/users/1/cv': { text: 'pdf-bytes', headers: { 'Content-Disposition': 'attachment; filename="cv.pdf"' } },
    })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.click(screen.getByRole('button', { name: 'Download CV' }))

    await waitFor(() => expect(downloads.click).toHaveBeenCalledOnce())
    expect(api.to('GET /api/users/1/cv')).toHaveLength(1)
  })

  test('GivenCv_WhenTogglingExtractedText_ThenTextIsShownThenHidden', async () => {
    mockApi({ 'GET /api/users/1/cv/text': { body: { text: 'Java developer with Spring' } } })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.click(screen.getByRole('button', { name: 'Show extracted text' }))
    expect(await screen.findByText('Java developer with Spring')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Hide extracted text' }))
    expect(screen.queryByText('Java developer with Spring')).not.toBeInTheDocument()
  })

  test('GivenUnreadableCv_WhenShowingText_ThenServerMessageIsShown', async () => {
    mockApi({
      'GET /api/users/1/cv/text': { status: 400, body: { message: 'Unsupported CV format. Use PDF, DOCX or TXT.' } },
    })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.click(screen.getByRole('button', { name: 'Show extracted text' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Unsupported CV format')
  })
})

describe('ProfilePage delete account', () => {
  test('GivenEmptyConfirmation_WhenOpening_ThenDeleteButtonIsDisabled', () => {
    mockApi({})
    renderApp('/profile')

    expect(screen.getByRole('button', { name: 'Delete my account' })).toBeDisabled()
  })

  test('GivenWrongEmailTyped_WhenTyping_ThenDeleteButtonStaysDisabled', async () => {
    mockApi({})
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('Type your email to confirm'), 'someone@else.com')

    expect(screen.getByRole('button', { name: 'Delete my account' })).toBeDisabled()
  })

  test('GivenOwnEmailInOtherCase_WhenConfirming_ThenAccountIsDeletedAndUserIsSentToLoginWithNotice', async () => {
    const api = mockApi({ 'DELETE /api/users/1': { status: 204 } })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('Type your email to confirm'), 'DANA@example.com')
    await user.click(screen.getByRole('button', { name: 'Delete my account' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your account was deleted.')
    expect(api.to('DELETE /api/users/1')).toHaveLength(1)
    expect(localStorage.getItem('ats.session')).toBeNull()
  })

  test('GivenServerFailure_WhenDeleting_ThenErrorIsShownAndSessionStays', async () => {
    mockApi({ 'DELETE /api/users/1': { status: 500, body: {} } })
    const user = userEvent.setup()
    renderApp('/profile')

    await user.type(screen.getByLabelText('Type your email to confirm'), 'dana@example.com')
    await user.click(screen.getByRole('button', { name: 'Delete my account' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The server hit an unexpected error')
    expect(localStorage.getItem('ats.session')).not.toBeNull()
  })
})
