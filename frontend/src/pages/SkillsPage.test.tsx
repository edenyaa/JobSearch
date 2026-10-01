import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import type { Skill } from '../api/types'
import { makeSkill, mockApi, renderApp } from '../test/helpers'

function statefulSkills(initial: Skill[] = []) {
  const store = [...initial]
  const api = mockApi({
    'GET /api/users/1/skills': () => ({ body: [...store] }),
    'POST /api/users/1/skills': (call) => {
      const input = call.body as Omit<Skill, 'id'>
      const created = { id: store.length + 10, ...input } as Skill
      store.push(created)
      return { status: 201, body: created }
    },
    'PUT /api/users/1/skills/1': (call) => {
      const input = call.body as Omit<Skill, 'id'>
      store[0] = { id: 1, ...input } as Skill
      return { body: store[0] }
    },
    'DELETE /api/users/1/skills/1': () => {
      store.splice(0, 1)
      return { status: 204 }
    },
  })
  return api
}

describe('SkillsPage', () => {
  test('GivenNoSkills_WhenOpening_ThenEmptyMessageIsShown', async () => {
    statefulSkills()
    renderApp('/skills')

    expect(await screen.findByText('No skills yet.')).toBeInTheDocument()
  })

  test('GivenSkills_WhenOpening_ThenNameExplanationAndKeywordsAreListed', async () => {
    statefulSkills([makeSkill({ keywords: ['Spring', 'JPA'] })])
    renderApp('/skills')

    const card = (await screen.findByText('Java')).closest('li')!

    expect(within(card).getByText('Backend language')).toBeInTheDocument()
    expect(within(card).getByText('Spring')).toBeInTheDocument()
    expect(within(card).getByText('JPA')).toBeInTheDocument()
  })

  test('GivenSkillFoundInCv_WhenListing_ThenItIsTaggedFromYourCv', async () => {
    statefulSkills([makeSkill({ source: 'CV' })])
    renderApp('/skills')

    const card = (await screen.findByText('Java')).closest('li')!

    expect(within(card).getByText('From your CV')).toBeInTheDocument()
  })

  test('GivenManualSkill_WhenListing_ThenItHasNoFromCvTag', async () => {
    statefulSkills([makeSkill({ source: 'MANUAL' })])
    renderApp('/skills')

    await screen.findByText('Java')

    expect(screen.queryByText('From your CV')).not.toBeInTheDocument()
  })

  test('GivenNameExplanationAndKeywords_WhenAdding_ThenSkillIsCreatedAndListed', async () => {
    const api = statefulSkills()
    const user = userEvent.setup()
    renderApp('/skills')
    await screen.findByText('No skills yet.')

    await user.type(screen.getByLabelText('Skill name'), 'Docker')
    await user.type(screen.getByLabelText('Explanation'), 'Containers')
    await user.type(screen.getByLabelText('Keywords'), 'containers{Enter}images{Enter}')
    await user.click(screen.getByRole('button', { name: 'Add skill' }))

    expect(await screen.findByText('Containers')).toBeInTheDocument()
    expect(api.to('POST /api/users/1/skills')[0].body).toEqual({
      name: 'Docker',
      explanation: 'Containers',
      keywords: ['containers', 'images'],
    })
    expect(screen.getByLabelText('Skill name')).toHaveValue('')
  })

  test('GivenPendingKeywordText_WhenAdding_ThenItIsIncludedBecauseBlurCommitsIt', async () => {
    const api = statefulSkills()
    const user = userEvent.setup()
    renderApp('/skills')
    await screen.findByText('No skills yet.')

    await user.type(screen.getByLabelText('Skill name'), 'SQL')
    await user.type(screen.getByLabelText('Keywords'), 'PostgreSQL')
    await user.click(screen.getByRole('button', { name: 'Add skill' }))

    await waitFor(() => expect(api.to('POST /api/users/1/skills')).toHaveLength(1))
    expect((api.to('POST /api/users/1/skills')[0].body as Skill).keywords).toEqual(['PostgreSQL'])
  })

  test('GivenBlankName_WhenAdding_ThenErrorIsShownAndNothingIsSent', async () => {
    const api = statefulSkills()
    const user = userEvent.setup()
    renderApp('/skills')
    await screen.findByText('No skills yet.')

    await user.click(screen.getByRole('button', { name: 'Add skill' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The skill name is required.')
    expect(api.to('POST /api/users/1/skills')).toHaveLength(0)
  })

  test('GivenServerError_WhenAdding_ThenMessageIsShown', async () => {
    mockApi({
      'GET /api/users/1/skills': { body: [] },
      'POST /api/users/1/skills': { status: 400, body: { message: 'Skill is invalid' } },
    })
    const user = userEvent.setup()
    renderApp('/skills')
    await screen.findByText('No skills yet.')

    await user.type(screen.getByLabelText('Skill name'), 'Docker')
    await user.click(screen.getByRole('button', { name: 'Add skill' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Skill is invalid')
  })

  test('GivenSkill_WhenClickingEdit_ThenFormIsPrefilled', async () => {
    statefulSkills([makeSkill({ keywords: ['Spring'] })])
    const user = userEvent.setup()
    renderApp('/skills')

    await user.click(await screen.findByRole('button', { name: 'Edit Java' }))

    expect(screen.getByRole('heading', { name: 'Edit skill' })).toBeInTheDocument()
    expect(screen.getByLabelText('Skill name')).toHaveValue('Java')
    expect(screen.getByLabelText('Explanation')).toHaveValue('Backend language')
    expect(within(screen.getByRole('button', { name: 'Remove Spring' }).parentElement!).getByText(/Spring/)).toBeInTheDocument()
  })

  test('GivenEditedSkill_WhenSaving_ThenItIsUpdatedAndFormResets', async () => {
    const api = statefulSkills([makeSkill()])
    const user = userEvent.setup()
    renderApp('/skills')
    await user.click(await screen.findByRole('button', { name: 'Edit Java' }))

    const name = screen.getByLabelText('Skill name')
    await user.clear(name)
    await user.type(name, 'Kotlin')
    await user.click(screen.getByRole('button', { name: 'Save skill' }))

    expect(await screen.findByText('Kotlin')).toBeInTheDocument()
    expect(api.to('PUT /api/users/1/skills/1')[0].body).toMatchObject({ name: 'Kotlin' })
    expect(screen.getByRole('heading', { name: 'Add a skill' })).toBeInTheDocument()
  })

  test('GivenEditing_WhenCancelling_ThenFormReturnsToAddMode', async () => {
    statefulSkills([makeSkill()])
    const user = userEvent.setup()
    renderApp('/skills')
    await user.click(await screen.findByRole('button', { name: 'Edit Java' }))

    await user.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(screen.getByRole('heading', { name: 'Add a skill' })).toBeInTheDocument()
    expect(screen.getByLabelText('Skill name')).toHaveValue('')
  })

  test('GivenSkill_WhenConfirmingDelete_ThenItDisappears', async () => {
    const api = statefulSkills([makeSkill()])
    const user = userEvent.setup()
    renderApp('/skills')
    const card = (await screen.findByText('Java')).closest('li')!

    await user.click(within(card).getByRole('button', { name: 'Delete' }))
    await user.click(within(card).getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByText('No skills yet.')).toBeInTheDocument()
    expect(api.to('DELETE /api/users/1/skills/1')).toHaveLength(1)
  })

  test('GivenSkill_WhenCancellingDelete_ThenSkillStays', async () => {
    const api = statefulSkills([makeSkill()])
    const user = userEvent.setup()
    renderApp('/skills')
    const card = (await screen.findByText('Java')).closest('li')!

    await user.click(within(card).getByRole('button', { name: 'Delete' }))
    await user.click(within(card).getByRole('button', { name: 'Cancel' }))

    expect(api.to('DELETE /api/users/1/skills/1')).toHaveLength(0)
    expect(screen.getByText('Java')).toBeInTheDocument()
  })

  test('GivenServerError_WhenLoading_ThenErrorIsShown', async () => {
    mockApi({ 'GET /api/users/1/skills': { status: 500, body: {} } })
    renderApp('/skills')

    expect(await screen.findByRole('alert')).toHaveTextContent('The server hit an unexpected error')
  })
})
