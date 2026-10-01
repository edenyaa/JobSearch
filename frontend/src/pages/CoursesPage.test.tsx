import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import type { Course } from '../api/types'
import { makeCourse, makeSkill, mockApi, renderApp } from '../test/helpers'

function statefulCourses(initial: Course[] = [], skills = [makeSkill(), makeSkill({ id: 2, name: 'Docker' })]) {
  const store = [...initial]
  return mockApi({
    'GET /api/users/1/skills': { body: skills },
    'GET /api/users/1/courses': () => ({ body: [...store] }),
    'POST /api/users/1/courses': (call) => {
      const created = { id: store.length + 10, ...(call.body as object) } as Course
      store.push(created)
      return { status: 201, body: created }
    },
    'PUT /api/users/1/courses/1': (call) => {
      store[0] = { id: 1, ...(call.body as object) } as Course
      return { body: store[0] }
    },
    'DELETE /api/users/1/courses/1': () => {
      store.splice(0, 1)
      return { status: 204 }
    },
  })
}

describe('CoursesPage', () => {
  test('GivenNoCourses_WhenOpening_ThenEmptyMessageIsShown', async () => {
    statefulCourses()
    renderApp('/courses')

    expect(await screen.findByText('No courses yet.')).toBeInTheDocument()
  })

  test('GivenCourseWithSkills_WhenOpening_ThenDetailsAssignmentsAndSkillNamesAreShown', async () => {
    statefulCourses([makeCourse({ skillIds: [1, 2], assignments: ['Built a compiler', 'Wrote a shell'] })])
    renderApp('/courses')

    const card = (await screen.findByText('CS101')).closest('li')!

    expect(within(card).getByText(/TAU/)).toBeInTheDocument()
    expect(within(card).getByText('Intro course')).toBeInTheDocument()
    expect(within(card).getByText('Built a compiler')).toBeInTheDocument()
    expect(within(card).getByText('Wrote a shell')).toBeInTheDocument()
    expect(within(card).getByText('Java')).toBeInTheDocument()
    expect(within(card).getByText('Docker')).toBeInTheDocument()
  })

  test('GivenFilledForm_WhenAdding_ThenCourseIsCreatedWithAssignmentsAndLinkedSkills', async () => {
    const api = statefulCourses()
    const user = userEvent.setup()
    renderApp('/courses')
    await screen.findByText('No courses yet.')

    await user.type(screen.getByLabelText('Course number'), 'CS350')
    await user.type(screen.getByLabelText('Where you studied'), 'TAU')
    await user.type(screen.getByLabelText('Description'), 'Cloud computing')
    await user.type(screen.getByLabelText('Assignments (one per line)'), 'Deployed an app{Enter}{Enter}Wrote Terraform')
    await user.click(screen.getByRole('checkbox', { name: 'Docker' }))
    await user.click(screen.getByRole('button', { name: 'Add course' }))

    expect(await screen.findByText('Cloud computing')).toBeInTheDocument()
    expect(api.to('POST /api/users/1/courses')[0].body).toEqual({
      courseNumber: 'CS350',
      institution: 'TAU',
      description: 'Cloud computing',
      assignments: ['Deployed an app', 'Wrote Terraform'],
      skillIds: [2],
    })
    expect(screen.getByLabelText('Course number')).toHaveValue('')
  })

  test('GivenBlankCourseNumber_WhenAdding_ThenErrorIsShownAndNothingIsSent', async () => {
    const api = statefulCourses()
    const user = userEvent.setup()
    renderApp('/courses')
    await screen.findByText('No courses yet.')

    await user.click(screen.getByRole('button', { name: 'Add course' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The course number is required.')
    expect(api.to('POST /api/users/1/courses')).toHaveLength(0)
  })

  test('GivenNoSkills_WhenOpening_ThenHintToAddSkillsIsShown', async () => {
    statefulCourses([], [])
    renderApp('/courses')

    expect(await screen.findByRole('link', { name: 'Add skills' })).toHaveAttribute('href', '/skills')
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  })

  test('GivenSkillCheckbox_WhenTogglingTwice_ThenItIsNotLinked', async () => {
    const api = statefulCourses()
    const user = userEvent.setup()
    renderApp('/courses')
    await screen.findByText('No courses yet.')

    await user.type(screen.getByLabelText('Course number'), 'X1')
    await user.click(screen.getByRole('checkbox', { name: 'Java' }))
    await user.click(screen.getByRole('checkbox', { name: 'Java' }))
    await user.click(screen.getByRole('button', { name: 'Add course' }))

    await screen.findByText('X1')
    expect((api.to('POST /api/users/1/courses')[0].body as Course).skillIds).toEqual([])
  })

  test('GivenCourse_WhenClickingEdit_ThenFormIsPrefilledIncludingSkillsAndAssignments', async () => {
    statefulCourses([makeCourse({ skillIds: [2], assignments: ['One', 'Two'] })])
    const user = userEvent.setup()
    renderApp('/courses')

    await user.click(await screen.findByRole('button', { name: 'Edit CS101' }))

    expect(screen.getByRole('heading', { name: 'Edit course' })).toBeInTheDocument()
    expect(screen.getByLabelText('Course number')).toHaveValue('CS101')
    expect(screen.getByLabelText('Assignments (one per line)')).toHaveValue('One\nTwo')
    expect(screen.getByRole('checkbox', { name: 'Docker' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'Java' })).not.toBeChecked()
  })

  test('GivenEditedCourse_WhenSaving_ThenItIsUpdated', async () => {
    const api = statefulCourses([makeCourse()])
    const user = userEvent.setup()
    renderApp('/courses')
    await user.click(await screen.findByRole('button', { name: 'Edit CS101' }))

    const number = screen.getByLabelText('Course number')
    await user.clear(number)
    await user.type(number, 'CS102')
    await user.click(screen.getByRole('button', { name: 'Save course' }))

    expect(await screen.findByText('CS102')).toBeInTheDocument()
    expect(api.to('PUT /api/users/1/courses/1')[0].body).toMatchObject({ courseNumber: 'CS102' })
  })

  test('GivenCourse_WhenConfirmingDelete_ThenItDisappears', async () => {
    const api = statefulCourses([makeCourse()])
    const user = userEvent.setup()
    renderApp('/courses')
    const card = (await screen.findByText('CS101')).closest('li')!

    await user.click(within(card).getByRole('button', { name: 'Delete' }))
    await user.click(within(card).getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByText('No courses yet.')).toBeInTheDocument()
    expect(api.to('DELETE /api/users/1/courses/1')).toHaveLength(1)
  })

  test('GivenServerError_WhenAdding_ThenMessageIsShown', async () => {
    mockApi({
      'GET /api/users/1/skills': { body: [] },
      'GET /api/users/1/courses': { body: [] },
      'POST /api/users/1/courses': { status: 404, body: { message: 'Skill 9 not found' } },
    })
    const user = userEvent.setup()
    renderApp('/courses')
    await screen.findByText('No courses yet.')

    await user.type(screen.getByLabelText('Course number'), 'CS1')
    await user.click(screen.getByRole('button', { name: 'Add course' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Skill 9 not found')
  })
})
